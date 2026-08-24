package com.mustafatetik.atomcv.jobs.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The transitions, and the lock each of them has to let go of. */
class JobTest {

    private static final Instant NOW = Instant.parse("2026-08-24T09:00:00Z");

    @Test
    void ajobTakesItsPriorityFromItsType() {
        assertThat(job(JobType.GENERATION).getPriority()).isEqualTo((short) 10);
        assertThat(job(JobType.MEASUREMENT).getPriority()).isEqualTo((short) 200);
        assertThat(JobType.GENERATION.priority()).isLessThan(JobType.EMBEDDING.priority());
    }

    @Test
    void anewJobIsQueuedAndRunnableNow() {
        var job = job(JobType.GENERATION);

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getRunAfter()).isEqualTo(NOW);
        assertThat(job.getProgress()).isEqualTo(JobProgress.NONE);
        assertThat(job.getError()).isNull();
        assertThat(job.getResult()).isNull();
    }

    /**
     * A retried job that kept its {@code locked_by} would be taken by the
     * claim and reclaimed by the zombie collector at the same time, and the
     * same work would run twice.
     */
    @Test
    void everyTerminalTransitionReleasesTheLock() {
        for (var transition : java.util.List.<java.util.function.Consumer<Job>>of(
                job -> job.succeed(Map.of("generationId", "x"), NOW),
                job -> job.fail(Map.of("code", "INTERNAL_ERROR"), NOW),
                job -> job.retryAfter(NOW.plusSeconds(4)),
                job -> job.cancel(NOW))) {

            var job = job(JobType.GENERATION);
            transition.accept(job);

            assertThat(job.getLockedBy()).isNull();
            assertThat(job.getLockedAt()).isNull();
            assertThat(job.getHeartbeatAt()).isNull();
        }
    }

    @Test
    void aretriedJobGoesBackToQueuedAndWaits() {
        var job = job(JobType.GENERATION);

        job.retryAfter(NOW.plusSeconds(4));

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getRunAfter()).isEqualTo(NOW.plusSeconds(4));
        assertThat(job.getCompletedAt()).isNull();
    }

    /** Succeeding after a failed attempt clears the error it left behind. */
    @Test
    void succeedingClearsAnEarlierError() {
        var job = job(JobType.GENERATION);
        job.fail(Map.of("code", "INTERNAL_ERROR"), NOW);

        job.succeed(Map.of("generationId", "x"), NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getError()).isNull();
    }

    @Test
    void onlyTheThreeTerminalStatusesSayTheyAre() {
        assertThat(JobStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(JobStatus.FAILED.isTerminal()).isTrue();
        assertThat(JobStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(JobStatus.QUEUED.isTerminal()).isFalse();
        assertThat(JobStatus.RUNNING.isTerminal()).isFalse();
    }

    @Test
    void amaxAttemptsBelowOneWouldQueueSomethingThatNeverRuns() {
        assertThatThrownBy(() -> job(JobType.GENERATION).setMaxAttempts((short) 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void apercentageOutsideTheRangeIsRefused() {
        assertThatThrownBy(() -> new JobProgress("C", "Selecting", 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobProgress("C", "Selecting", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Absolute rule 4: the payload is built from user content. */
    @Test
    void thetoStringCarriesNoPayload() {
        var job = new Job(JobType.GENERATION, UUID.randomUUID(),
                Map.of("jobDescription", "We are hiring a backend engineer at Acme"), NOW);

        assertThat(job.toString()).doesNotContain("Acme", "backend", "hiring");
    }

    private static Job job(JobType type) {
        return new Job(type, UUID.randomUUID(), Map.of("k", "v"), NOW);
    }
}
