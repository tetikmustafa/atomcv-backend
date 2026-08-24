package com.mustafatetik.atomcv.jobs.workers;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobHandler;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobRetryPolicy;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * One instance working the queue (Bolum 30.2, 30.4).
 *
 * <p>Everything the scheduler calls is also callable directly, and the
 * integration tests call it directly with the scheduler off. A queue tested
 * through a timer is a queue tested by sleeping, and the properties worth
 * proving here — that two workers never take the same row, that a silent
 * worker's jobs come back, that a shutdown does not strand them — are all
 * about ordering rather than about elapsed time.
 */
@Component
@ConditionalOnProperty(
        name = "atomcv.jobs.worker.enabled", havingValue = "true", matchIfMissing = true)
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobQueue queue;
    private final Map<JobType, JobHandler> handlers;
    private final JobWorkerProperties properties;
    private final Clock clock;
    private final RandomGenerator random;

    private final String workerId;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor;

    private volatile boolean acceptingNewJobs = true;

    JobWorker(JobQueue queue, List<JobHandler> handlers, JobWorkerProperties properties,
            Clock clock) {
        this(queue, handlers, properties, clock, RandomGenerator.getDefault());
    }

    JobWorker(JobQueue queue, List<JobHandler> handlers, JobWorkerProperties properties,
            Clock clock, RandomGenerator random) {

        this.queue = queue;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                JobHandler::type, handler -> handler));
        this.properties = properties;
        this.clock = clock;
        this.random = random;
        this.workerId = newWorkerId();
        this.executor = Executors.newFixedThreadPool(properties.concurrency());
        log.info("Worker {} handling {} with concurrency {}",
                workerId, this.handlers.keySet(), properties.concurrency());
    }

    /** Identifies the claim in {@code locked_by}, and only has to be unique. */
    public String workerId() {
        return workerId;
    }

    // ── the loop ─────────────────────────────────────────────────────────

    /**
     * Take as much as there is room for, and run it.
     *
     * <p>Claims one at a time rather than in a batch: {@code SKIP LOCKED}
     * decides per row, and a batch claim would hold rows this instance may not
     * get to before another instance would have.
     */
    @Scheduled(
            fixedDelayString = "${atomcv.jobs.worker.poll-interval:PT0.5S}",
            initialDelayString = "${atomcv.jobs.worker.poll-interval:PT0.5S}")
    public void poll() {
        while (acceptingNewJobs && running.size() < properties.concurrency()) {
            Optional<UUID> claimed = queue.claim(workerId);
            if (claimed.isEmpty()) {
                return;
            }
            UUID jobId = claimed.get();
            running.add(jobId);
            executor.submit(() -> run(jobId));
        }
    }

    /**
     * Claim one job and run it to a terminal state, in this thread.
     *
     * @return whether there was one to take
     */
    public boolean runOne() {
        Optional<UUID> claimed = queue.claim(workerId);
        if (claimed.isEmpty()) {
            return false;
        }
        UUID jobId = claimed.get();
        running.add(jobId);
        run(jobId);
        return true;
    }

    private void run(UUID jobId) {
        try {
            Job job = queue.find(jobId).orElseThrow(() -> new IllegalStateException(
                    "The job just claimed is gone: " + jobId));
            settle(job, outcomeOf(job));
        } catch (RuntimeException unexpected) {
            // The row could not even be read, or settling it threw. Nothing
            // left to write to; the zombie collector takes it from here.
            log.error("Worker {} could not settle job {}", workerId, jobId, unexpected);
        } finally {
            running.remove(jobId);
        }
    }

    private JobOutcome outcomeOf(Job job) {
        JobHandler handler = handlers.get(job.getType());
        if (handler == null) {
            // A queued row with nobody to run it is a deployment mistake. It
            // fails rather than waiting, because a job that waits forever is
            // invisible and this one is at least counted.
            log.error("No handler for {}; failing job {}", job.getType(), job.getId());
            return JobOutcome.failed(UserFacingError.of(ErrorCode.INTERNAL_ERROR), false);
        }
        try {
            return handler.handle(job);
        } catch (RuntimeException thrown) {
            // Unreasoned-about failures are retried while attempts remain: an
            // exception is by definition something nobody decided was final.
            // The message, never the payload (absolute rule 4).
            log.error("Handler for {} threw on job {}: {}",
                    job.getType(), job.getId(), thrown.toString());
            return JobOutcome.failed(UserFacingError.of(ErrorCode.INTERNAL_ERROR), true);
        }
    }

    private void settle(Job job, JobOutcome outcome) {
        switch (outcome) {
            case JobOutcome.Completed done -> job.succeed(done.result(), clock.instant());
            case JobOutcome.Failed failed -> {
                if (failed.retryable() && job.hasAttemptsLeft()) {
                    Duration wait = JobRetryPolicy.backoff(job.getAttempts(), random);
                    job.retryAfter(clock.instant().plus(wait));
                    log.info("Job {} failed with {} on attempt {}; retrying in {}",
                            job.getId(), failed.error().code(), job.getAttempts(), wait);
                } else {
                    job.fail(asStoredError(failed.error()), clock.instant());
                }
            }
        }
        queue.save(job);
    }

    /**
     * The error as {@code jobs.error} holds it, which is the shape EK D.6.4
     * publishes.
     *
     * <p>A {@code LinkedHashMap} rather than {@code Map.of}: the JDK's
     * immutable maps iterate in an order salted per JVM run, so the same
     * failure would serialise differently between runs and no two rows would
     * compare.
     */
    private static Map<String, Object> asStoredError(UserFacingError error) {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("code", error.code().name());
        stored.put("params", error.params());
        stored.put("resolutions", error.resolutions());
        return stored;
    }

    // ── staying alive ────────────────────────────────────────────────────

    /** Bolum 30.4: "still working", for everything this instance holds. */
    @Scheduled(
            fixedDelayString = "${atomcv.jobs.worker.heartbeat-every:PT20S}",
            initialDelayString = "${atomcv.jobs.worker.heartbeat-every:PT20S}")
    public void heartbeat() {
        Collection<UUID> held = Set.copyOf(running);
        if (!held.isEmpty()) {
            queue.touchHeartbeat(workerId, held);
        }
    }

    /**
     * Bolum 30.4: jobs whose worker stopped answering go back to the queue.
     *
     * <p>Every instance runs this, including against its own jobs. That is
     * safe and deliberate — the condition is the heartbeat, not the owner, so
     * an instance that has stalled long enough to look dead is treated as dead
     * by itself as readily as by anyone else.
     */
    @Scheduled(
            fixedDelayString = "${atomcv.jobs.worker.reclaim-every:PT60S}",
            initialDelayString = "${atomcv.jobs.worker.reclaim-every:PT60S}")
    public void reclaimStale() {
        queue.reclaimStale(properties.staleAfter());
    }

    /**
     * Bolum 30.4: stop taking work, finish what is in hand, hand back the rest.
     *
     * <p>Without this a rolling deploy leaves every in-flight job locked until
     * the collector notices — {@code staleAfter} of a spinner on a screen
     * someone is watching, on every single deploy.
     */
    @PreDestroy
    public void shutdown() {
        acceptingNewJobs = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(
                    properties.shutdownGrace().toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                int released = queue.releaseLocks(workerId);
                log.warn("Worker {} did not drain in time; released {} locks",
                        workerId, released);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            queue.releaseLocks(workerId);
        }
    }

    private static String newWorkerId() {
        String host;
        try {
            host = java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException unnamed) {
            host = "unknown";
        }
        // The random half is what makes it unique; the host half is what makes
        // a stuck locked_by tell an operator which box to look at.
        return host + "-" + ProcessHandle.current().pid() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }
}
