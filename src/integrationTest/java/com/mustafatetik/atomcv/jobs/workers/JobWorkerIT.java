package com.mustafatetik.atomcv.jobs.workers;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobHandler;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobStatus;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A worker taking a job to a terminal state (Bolum 30.4, 30.5).
 *
 * <p>The worker is built here rather than autowired: the scheduler is off for
 * the whole suite, and a queue tested through a timer is a queue tested by
 * sleeping. Every method the scheduler would call is called directly, so what
 * is being asserted is ordering rather than elapsed time.
 */
class JobWorkerIT extends AbstractIntegrationTest {

    private static final RandomGenerator NO_JITTER = new RandomGenerator() {
        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public long nextLong(long bound) {
            return 0;
        }
    };

    @Autowired
    private JobQueue queue;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private UUID userId;

    @BeforeEach
    void startFromAnEmptyQueue() {
        jdbc.update("DELETE FROM jobs");
        userId = jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
    }

    @Test
    void asuccessfulJobEndsCompletedWithItsResult() {
        Job queued = enqueue();
        var worker = workerRunning(job -> JobOutcome.completed(Map.of("pageCount", 1)));

        assertThat(worker.runOne()).isTrue();

        Job done = queue.find(queued.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(done.getResult()).containsEntry("pageCount", 1);
        assertThat(done.getCompletedAt()).isNotNull();
        assertThat(done.getLockedBy()).isNull();
    }

    /** Bolum 30.5: the world outside may have changed by the next attempt. */
    @Test
    void aretryableFailureGoesBackToTheQueueBehindItsBackoff() {
        Job queued = enqueue();
        var worker = workerRunning(job -> JobOutcome.failed(
                UserFacingError.of(ErrorCode.ALL_PROVIDERS_UNAVAILABLE), true));

        worker.runOne();

        Job retried = queue.find(queued.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(retried.getAttempts()).isEqualTo((short) 1);
        // 2^1 seconds with the jitter pinned at zero.
        assertThat(retried.getRunAfter()).isAfter(clock.instant().plusSeconds(1));
        assertThat(retried.getError()).isNull();
        // And the claim honours it, so the next poll does not pick it straight
        // back up — which would make the backoff decorative.
        assertThat(queue.claim("anyone")).isEmpty();
    }

    /**
     * A retry budget that is never spent is an infinite loop. The third
     * failure of a three-attempt job is final.
     */
    @Test
    void aretryableFailureOutOfAttemptsIsFailedInstead() {
        Job queued = enqueue();
        var worker = workerRunning(job -> JobOutcome.failed(
                UserFacingError.of(ErrorCode.ALL_PROVIDERS_UNAVAILABLE), true));

        for (int attempt = 1; attempt <= 3; attempt++) {
            jdbc.update("UPDATE jobs SET run_after = ? WHERE id = ?",
                    java.sql.Timestamp.from(clock.instant()), queued.getId());
            worker.runOne();
        }

        Job exhausted = queue.find(queued.getId()).orElseThrow();
        assertThat(exhausted.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(exhausted.getAttempts()).isEqualTo((short) 3);
    }

    /**
     * Not retried however many attempts remain. Reading the same thin profile
     * three times fails three times, and the user waits three backoffs for the
     * answer they would have had at once.
     */
    @Test
    void anonRetryableFailureIsFinalOnTheFirstAttempt() {
        Job queued = enqueue();
        var worker = workerRunning(job -> JobOutcome.failed(
                UserFacingError.with(ErrorCode.INSUFFICIENT_PROFILE)
                        .param("completeness", 10)
                        .param("missing", List.of("atoms"))
                        .build(),
                false));

        worker.runOne();

        Job failed = queue.find(queued.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo((short) 1);
        assertThat(failed.getError()).containsEntry("code", "INSUFFICIENT_PROFILE");
        assertThat(failed.getError()).containsKeys("params", "resolutions");
    }

    /**
     * An exception is by definition something nobody decided was final, so it
     * is retried while attempts remain.
     */
    @Test
    void ahandlerThatThrowsIsTreatedAsRetryable() {
        Job queued = enqueue();
        var worker = workerRunning(job -> {
            throw new IllegalStateException("the provider client blew up");
        });

        worker.runOne();

        Job retried = queue.find(queued.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(retried.getAttempts()).isEqualTo((short) 1);
    }

    /**
     * A queued row with nobody to run it is a deployment mistake. It fails
     * rather than waiting, because a job that waits forever is invisible.
     */
    @Test
    void ajobWithNoHandlerFailsRatherThanSittingInTheQueue() {
        Job queued = enqueue();
        var worker = new JobWorker(queue, List.of(), properties(Duration.ofSeconds(30)),
                clock, NO_JITTER);

        worker.runOne();

        Job failed = queue.find(queued.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.getError()).containsEntry("code", "INTERNAL_ERROR");
    }

    @Test
    void anemptyQueueIsNotAFailure() {
        assertThat(workerRunning(job -> JobOutcome.completed(Map.of())).runOne()).isFalse();
    }

    /** Bolum 30.4: "still working", and only about this worker's own jobs. */
    @Test
    void theheartbeatRefreshesWhatTheWorkerHolds() throws Exception {
        enqueue();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var worker = workerRunning(job -> {
            started.countDown();
            await(release);
            return JobOutcome.completed(Map.of());
        });

        var thread = new Thread(worker::runOne);
        thread.start();
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        jdbc.update("UPDATE jobs SET heartbeat_at = ? WHERE status = 'running'",
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofMinutes(10))));

        worker.heartbeat();

        // Refreshed, so the collector no longer sees a zombie.
        assertThat(worker.workerId()).isNotBlank();
        assertThat(queue.reclaimStale(Duration.ofMinutes(2))).isZero();
        release.countDown();
        thread.join(TimeUnit.SECONDS.toMillis(10));
    }

    /**
     * Bolum 30.4: a shutdown that gave up without handing the job back would
     * leave it locked until the collector noticed — {@code staleAfter} of dead
     * time on a screen, on every single deploy.
     */
    @Test
    void ashutdownThatCannotDrainHandsTheJobBack() throws Exception {
        Job queued = enqueue();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var worker = new JobWorker(queue,
                List.of(handler(job -> {
                    started.countDown();
                    await(release);
                    return JobOutcome.completed(Map.of());
                })),
                properties(Duration.ofSeconds(1)), clock, NO_JITTER);

        worker.poll();
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

        worker.shutdown();

        assertThat(queue.find(queued.getId()).orElseThrow().getStatus())
                .isEqualTo(JobStatus.QUEUED);
        release.countDown();
    }

    /** The claim is what stops two workers taking one row; the loop respects it. */
    @Test
    void aworkerTakesNoMoreThanItsConcurrency() {
        for (int index = 0; index < 5; index++) {
            enqueue();
        }
        var handled = new AtomicInteger();
        var release = new CountDownLatch(1);
        var worker = new JobWorker(queue,
                List.of(handler(job -> {
                    handled.incrementAndGet();
                    await(release);
                    return JobOutcome.completed(Map.of());
                })),
                properties(Duration.ofSeconds(1)), clock, NO_JITTER);

        worker.poll();

        try {
            assertThat(Integer.valueOf(jdbc.queryForObject(
                    "SELECT count(*) FROM jobs WHERE status = 'running'", Integer.class)))
                    .isEqualTo(2);
        } finally {
            release.countDown();
            worker.shutdown();
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private Job enqueue() {
        return queue.enqueue(new Job(JobType.GENERATION, userId,
                Map.of("jobDescription", "irrelevant"), clock.instant()));
    }

    private JobWorker workerRunning(Function<Job, JobOutcome> body) {
        return new JobWorker(queue, List.of(handler(body)),
                properties(Duration.ofSeconds(30)), clock, NO_JITTER);
    }

    private static JobHandler handler(Function<Job, JobOutcome> body) {
        return new JobHandler() {
            @Override
            public JobType type() {
                return JobType.GENERATION;
            }

            @Override
            public JobOutcome handle(Job job) {
                return body.apply(job);
            }
        };
    }

    private static JobWorkerProperties properties(Duration shutdownGrace) {
        return new JobWorkerProperties(true, 2, Duration.ofMillis(500),
                Duration.ofSeconds(20), Duration.ofMinutes(2), shutdownGrace);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the handler");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
