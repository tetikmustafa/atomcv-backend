package com.mustafatetik.atomcv.jobs.workers;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobProgress;
import com.mustafatetik.atomcv.jobs.queue.JobStatus;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Bolum 48.3's three missing rows, measured against a clock that does not move
 * on its own.
 *
 * <p>A real clock would make every one of these an assertion about how fast
 * this machine is, which is the shape of test that passes locally and fails on
 * a loaded runner. Here the clock is stepped by hand, so the numbers are exact
 * and what is being tested is the arithmetic: which interval belongs to which
 * phase.
 */
class JobTelemetryTest {

    private static final Instant START = Instant.parse("2026-09-15T10:00:00Z");

    private SimpleMeterRegistry meters;
    private SteppingClock clock;
    private JobTelemetry telemetry;

    @BeforeEach
    void wire() {
        meters = new SimpleMeterRegistry();
        clock = new SteppingClock(START);
        telemetry = new JobTelemetry(meters, clock);
    }

    /**
     * <strong>A phase is the interval between its report and the next.</strong>
     * Three phases, three different lengths, and each has to land on its own
     * timer — a version that closed the wrong one would still produce three
     * measurements adding to the same total.
     */
    @Test
    void eachPhaseIsTimedFromItsOwnReportToTheNext() {
        JobTelemetry.Run run = telemetry.started(queued());

        run.reported(at("A"));
        clock.advance(Duration.ofSeconds(3));
        run.reported(at("B"));
        clock.advance(Duration.ofSeconds(7));
        run.reported(at("D"));
        clock.advance(Duration.ofSeconds(11));
        run.finished(JobStatus.COMPLETED);

        assertThat(millisOf(phase("A"))).isEqualTo(3_000);
        assertThat(millisOf(phase("B"))).isEqualTo(7_000);
        assertThat(millisOf(phase("D"))).isEqualTo(11_000);
    }

    /**
     * The last phase is closed by the run finishing. Without that it would be
     * the one phase never measured — and it is the one a slow generation is
     * usually sitting in.
     */
    @Test
    void thelastPhaseIsClosedByTheRunEnding() {
        JobTelemetry.Run run = telemetry.started(queued());

        run.reported(at("G"));
        clock.advance(Duration.ofSeconds(5));
        run.finished(JobStatus.COMPLETED);

        assertThat(phase("G").count()).isEqualTo(1);
        assertThat(millisOf(phase("G"))).isEqualTo(5_000);
    }

    /**
     * <strong>A phase that reports itself twice is one phase.</strong> The
     * rewrite loop reports the same phase on every compile attempt, and
     * counting each as a fresh measurement would make the p95 improve the more
     * often the progress bar was refreshed — a metric that rewards the thing
     * that has nothing to do with it.
     */
    @Test
    void thesamePhaseReportedTwiceIsOneMeasurement() {
        JobTelemetry.Run run = telemetry.started(queued());

        run.reported(at("D"));
        clock.advance(Duration.ofSeconds(4));
        run.reported(at("D"));
        clock.advance(Duration.ofSeconds(6));
        run.finished(JobStatus.COMPLETED);

        assertThat(phase("D").count()).as("one phase, not two").isEqualTo(1);
        assertThat(millisOf(phase("D"))).isEqualTo(10_000);
    }

    /** An empty phase is not a phase (F-010); it must not open a timer. */
    @Test
    void anemptyPhaseIsNotMeasured() {
        JobTelemetry.Run run = telemetry.started(queued());

        run.reported(JobProgress.NONE);
        clock.advance(Duration.ofSeconds(2));
        run.finished(JobStatus.COMPLETED);

        assertThat(meters.find(JobTelemetry.PHASE).timers()).isEmpty();
    }

    /**
     * The success rate of Bolum 48.3 is the outcome tag: a failed run is
     * measured too, and under its own name.
     */
    @Test
    void afailedRunIsMeasuredSeparately() {
        telemetry.started(queued()).finished(JobStatus.FAILED);

        assertThat(run("failed").count()).isEqualTo(1);
        assertThat(meters.find(JobTelemetry.RUN).tag("outcome", "completed").timer()).isNull();
    }

    /**
     * <strong>The wait is the queue's, not the backoff's.</strong> A retry is
     * held back on purpose (Bolum 30.5), and counting that hold as queue
     * pressure would make every failing provider look like a machine that
     * needs to be bigger — which is the confusion Bolum 50.4 exists to stop.
     */
    @Test
    void thequeueWaitIsMeasuredFromWhenTheJobBecameRunnable() {
        Job job = queued();
        clock.advance(Duration.ofSeconds(9));

        telemetry.started(job);

        assertThat(millisOf(meters.get(JobTelemetry.QUEUE_WAIT)
                .tag("type", "generation").timer())).isEqualTo(9_000);
    }

    /** A clock that went backwards is not a negative wait. */
    @Test
    void aclockThatWentBackwardsRecordsNoWait() {
        Job job = queued();
        clock.advance(Duration.ofSeconds(-5));

        telemetry.started(job);

        assertThat(meters.find(JobTelemetry.QUEUE_WAIT).timers()).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Job queued() {
        return new Job(JobType.GENERATION, UUID.randomUUID(), Map.of(), clock.instant());
    }

    private static JobProgress at(String phase) {
        return new JobProgress(phase, "generation.phase." + phase, 10, null);
    }

    private Timer phase(String phase) {
        return meters.get(JobTelemetry.PHASE)
                .tag("type", "generation").tag("phase", phase).timer();
    }

    private Timer run(String outcome) {
        return meters.get(JobTelemetry.RUN).tag("outcome", outcome).timer();
    }

    private static long millisOf(Timer timer) {
        return (long) timer.totalTime(TimeUnit.MILLISECONDS);
    }

    /** Moves only when a test says so. */
    private static final class SteppingClock extends Clock {

        private Instant now;

        private SteppingClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
