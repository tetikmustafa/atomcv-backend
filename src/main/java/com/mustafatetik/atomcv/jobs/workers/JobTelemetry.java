package com.mustafatetik.atomcv.jobs.workers;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobProgress;
import com.mustafatetik.atomcv.jobs.queue.JobStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * What a run of the queue costs, in the shape Bolum 48.3's table asks for.
 *
 * <p>Three of that table's rows have no other source. <strong>Per-phase p50/p95
 * latency</strong> is the first thing anyone asks when a generation feels slow,
 * and until this existed the only answer was a stopwatch on a log line.
 * <strong>Success rate</strong> was countable only by querying the jobs table,
 * which is a different question — it counts rows, not runs, and a retried job
 * is one row and two runs. <strong>Queue wait</strong> is Bolum 50.4's first
 * diagnostic: a pipeline that got slower because work is waiting to start is
 * the one case where a bigger machine actually helps, and the one this
 * distinguishes.
 *
 * <p><strong>The phase boundary is the progress report.</strong> A phase ends
 * when the next one is announced — the same event the user's progress bar
 * moves on — so nothing has to be wrapped by hand at seven call sites, and a
 * phase that is added later is measured on the day it starts reporting. The
 * cost is that the last phase of a run is closed by the run finishing rather
 * than by its successor, which is exactly what it is.
 *
 * <p>Tags are the job type and the phase key, both closed sets. Nothing here
 * carries an id: a per-job tag would make a time series per generation, which
 * is how a metrics bill outgrows the thing it measures.
 */
@Component
public class JobTelemetry {

    /** Bolum 48.3, "Faz bazinda p50/p95 gecikme". */
    static final String PHASE = "job.phase";

    /** The same row's "basari orani": the outcome tag is the rate. */
    static final String RUN = "job.run";

    /** Bolum 48.3's system row, and Bolum 50.4's first question. */
    static final String QUEUE_WAIT = "job.queue.wait";

    private final MeterRegistry meters;
    private final Clock clock;

    public JobTelemetry(MeterRegistry meters, Clock clock) {
        this.meters = meters;
        this.clock = clock;
    }

    /**
     * Starts measuring one run, and records what it already waited.
     *
     * <p>From {@code runAfter} rather than from {@code createdAt}: a retry is
     * deliberately held back (Bolum 30.5) and a job that waited its backoff
     * correctly is not a queue that is behind. For a first attempt the two are
     * the same instant.
     */
    public Run started(Job job) {
        Duration waited = Duration.between(job.getRunAfter(), clock.instant());
        if (!waited.isNegative()) {
            timer(QUEUE_WAIT, "type", typeOf(job)).record(waited);
        }
        return new Run(job);
    }

    /** One run of one job, from claim to terminal state. */
    public final class Run {

        private final String type;
        private final long startedAt;
        private String phase;
        private long phaseStartedAt;

        private Run(Job job) {
            this.type = typeOf(job);
            this.startedAt = clock.millis();
            this.phaseStartedAt = startedAt;
        }

        /**
         * A phase was reported. Closes whatever was running before it.
         *
         * <p>The same report twice — which a handler is allowed to make, and
         * the rewrite loop does — is one phase, not two. A phase that
         * re-announced itself would otherwise be two short measurements where
         * there was one long one, and the p95 would read better the more often
         * the bar was refreshed.
         */
        public void reported(JobProgress progress) {
            String reported = progress == null ? null : progress.phase();
            if (reported == null || reported.isBlank() || reported.equals(phase)) {
                return;
            }
            closeCurrentPhase();
            phase = reported;
        }

        /** The run reached a state it will not leave. */
        public void finished(JobStatus status) {
            closeCurrentPhase();
            timer(RUN, "type", type, "outcome", tagOf(status))
                    .record(Duration.ofMillis(clock.millis() - startedAt));
        }

        private void closeCurrentPhase() {
            long now = clock.millis();
            if (phase != null) {
                timer(PHASE, "type", type, "phase", phase)
                        .record(Duration.ofMillis(now - phaseStartedAt));
            }
            phaseStartedAt = now;
        }
    }

    private Timer timer(String name, String... tags) {
        return meters.timer(name, tags);
    }

    private static String typeOf(Job job) {
        return job.getType().name().toLowerCase(Locale.ROOT);
    }

    private static String tagOf(JobStatus status) {
        return status == null ? "unknown" : status.name().toLowerCase(Locale.ROOT);
    }
}
