package com.mustafatetik.atomcv.jobs.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.shared.error.CompilationFailureKind;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import java.time.Duration;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** Bolum 30.5: which failures are worth trying again, and how long to wait. */
class JobRetryPolicyTest {

    /** Zero jitter, so the formula is what is being measured. */
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

    /**
     * The world outside changes: a provider comes back, TeX is redeployed.
     */
    @Test
    void failuresOfTheOutsideWorldAreRetried() {
        assertThat(JobRetryPolicy.isRetryable(
                new PipelineError.AllProvidersUnavailable(List.of("openrouter")))).isTrue();
        assertThat(JobRetryPolicy.isRetryable(new PipelineError.CompilationFailed(
                CompilationFailureKind.UNAVAILABLE, ""))).isTrue();
    }

    /**
     * Not "was this the user's fault" — nobody's fault either way. The
     * question is whether the next attempt reads anything different, and all
     * four of these read the same inputs and reach the same answer.
     */
    @Test
    void failuresDecidedByTheInputsAreNot() {
        assertThat(JobRetryPolicy.isRetryable(
                new PipelineError.InsufficientProfile(10, List.of("atoms")))).isFalse();
        assertThat(JobRetryPolicy.isRetryable(
                new PipelineError.UnparseableJobDescription(0.2, 0))).isFalse();
        assertThat(JobRetryPolicy.isRetryable(
                new PipelineError.ConflictingPreferences(900, 700, List.of()))).isFalse();
        assertThat(JobRetryPolicy.isRetryable(
                new PipelineError.PageLimitExceeded(3, 1))).isFalse();
    }

    /** Bolum 30.5's formula, with the jitter held at zero. */
    @Test
    void thewaitDoublesWithEachAttempt() {
        assertThat(JobRetryPolicy.backoff(1, NO_JITTER)).isEqualTo(Duration.ofSeconds(2));
        assertThat(JobRetryPolicy.backoff(2, NO_JITTER)).isEqualTo(Duration.ofSeconds(4));
        assertThat(JobRetryPolicy.backoff(3, NO_JITTER)).isEqualTo(Duration.ofSeconds(8));
    }

    /**
     * Without a cap the shift overflows a long at 63 attempts and the delay
     * comes back negative — the job would run immediately, and go on doing so
     * forever. A job cannot normally be attempted that often, but the zombie
     * collector can hand one back more times than its retry budget suggests.
     */
    @Test
    void thewaitIsCappedAndNeverNegative() {
        assertThat(JobRetryPolicy.backoff(60, NO_JITTER)).isEqualTo(Duration.ofMinutes(5));
        assertThat(JobRetryPolicy.backoff(Integer.MAX_VALUE, NO_JITTER))
                .isEqualTo(Duration.ofMinutes(5));
    }

    /**
     * The jitter is not decoration. Every job that failed to one outage comes
     * back at the same instant without it, and fails together again.
     */
    @Test
    void thejitterStaysWithinASecond() {
        var maximum = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 999;
            }

            @Override
            public long nextLong(long bound) {
                return bound - 1;
            }
        };

        assertThat(JobRetryPolicy.backoff(1, maximum))
                .isEqualTo(Duration.ofMillis(2999));
    }

    @Test
    void aretryFollowsAnAttempt() {
        assertThatThrownBy(() -> JobRetryPolicy.backoff(0, NO_JITTER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
