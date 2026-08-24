package com.mustafatetik.atomcv.jobs.sse;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobEvents;
import com.mustafatetik.atomcv.jobs.queue.JobStatus;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Who is watching which job (Bolum 30.6).
 *
 * <p><strong>In-process, and that is a decision with a date on it.</strong>
 * One instance runs the workers and serves the streams, so a publish reaches
 * every watcher. The moment there are two instances a watcher connected to A
 * will hear nothing about a job running on B; Bolum 30.6 names Postgres
 * {@code LISTEN/NOTIFY} as the way out, and the status endpoint is the
 * fallback that already works either way.
 *
 * <p>Every event carries an id, which orders the events of one stream and
 * nothing more. Replaying from a {@code Last-Event-ID} would need a buffer per
 * job, and EK D.6.4 accepts the cheaper honest alternative: on subscribe the
 * current state is sent immediately, so a reconnecting client is caught up
 * whether or not it remembered where it was.
 */
@Component
public class SseRegistry implements JobEvents {

    private static final Logger log = LoggerFactory.getLogger(SseRegistry.class);

    /** Bolum 30.6. A generation that outruns this is one the client re-polls. */
    static final long TIMEOUT_MS = java.time.Duration.ofMinutes(5).toMillis();

    private final Map<UUID, List<SseEmitter>> watchers = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> sequences = new ConcurrentHashMap<>();

    /**
     * Starts watching, and says at once where the job already is.
     *
     * <p>The immediate event is what makes reconnection work without a replay
     * buffer, and it is also what stops the worst failure in this whole
     * subsystem: a job that finished between the 202 and the subscribe would
     * otherwise never send anything, and the page would spin forever over work
     * that was already done.
     *
     * <p>The emitter is built here rather than handed in, so the timeout of
     * Bolum 30.6 is decided in one place instead of at every call site.
     */
    public SseEmitter subscribe(Job job) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        UUID jobId = job.getId();
        emitter.onCompletion(() -> remove(jobId, emitter));
        emitter.onTimeout(() -> remove(jobId, emitter));
        emitter.onError(failure -> remove(jobId, emitter));

        if (job.getStatus().isTerminal()) {
            // Nothing to watch. Say what happened and close, rather than
            // holding a connection open for five minutes over nothing.
            sendTo(emitter, jobId, terminalEvent(job), payloadOf(job));
            emitter.complete();
            return emitter;
        }

        watchers.computeIfAbsent(jobId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        sendTo(emitter, jobId, "phase", job.getProgress());
        return emitter;
    }

    @Override
    public void progress(Job job) {
        broadcast(job.getId(), "phase", job.getProgress());
    }

    @Override
    public void terminal(Job job) {
        UUID jobId = job.getId();
        broadcast(jobId, terminalEvent(job), payloadOf(job));
        // Close every stream on this job: there will be no further events, and
        // a client that keeps the connection open learns nothing by waiting.
        for (SseEmitter emitter : watchers.getOrDefault(jobId, List.of())) {
            emitter.complete();
        }
        watchers.remove(jobId);
        sequences.remove(jobId);
    }

    public void remove(UUID jobId, SseEmitter emitter) {
        List<SseEmitter> forJob = watchers.get(jobId);
        if (forJob == null) {
            return;
        }
        forJob.remove(emitter);
        if (forJob.isEmpty()) {
            // Both maps together, or an abandoned job leaks a counter for the
            // lifetime of the process.
            watchers.remove(jobId);
            sequences.remove(jobId);
        }
    }

    /** How many streams are open on a job. For tests and for a metric later. */
    public int watcherCount(UUID jobId) {
        return watchers.getOrDefault(jobId, List.of()).size();
    }

    private void broadcast(UUID jobId, String event, Object payload) {
        for (SseEmitter emitter : watchers.getOrDefault(jobId, List.of())) {
            sendTo(emitter, jobId, event, payload);
        }
    }

    /**
     * A send that fails takes its watcher out and nothing else.
     *
     * <p>A closed browser tab is the ordinary case, not an incident: the
     * client is gone, the job carries on, and the row is still there for
     * whoever asks next.
     */
    private void sendTo(SseEmitter emitter, UUID jobId, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(nextId(jobId)))
                    .name(event)
                    .data(payload));
        } catch (IOException | IllegalStateException gone) {
            log.debug("Dropping a watcher of job {}: {}", jobId, gone.toString());
            remove(jobId, emitter);
        }
    }

    private long nextId(UUID jobId) {
        return sequences.computeIfAbsent(jobId, id -> new AtomicLong()).incrementAndGet();
    }

    private static String terminalEvent(Job job) {
        return job.getStatus() == JobStatus.COMPLETED ? "completed" : "failed";
    }

    private static Object payloadOf(Job job) {
        return job.getStatus() == JobStatus.COMPLETED
                ? orEmpty(job.getResult())
                : orEmpty(job.getError());
    }

    private static Map<String, Object> orEmpty(Map<String, Object> values) {
        return values == null ? Map.of() : values;
    }
}
