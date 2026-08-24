package com.mustafatetik.atomcv.jobs.queue;

import com.mustafatetik.atomcv.shared.error.PipelineError;
import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * Which failures are worth trying again, and how long to wait (Bolum 30.5).
 *
 * <p>The switch is exhaustive over a sealed interface, so a new kind of
 * failure does not compile until someone has decided whether retrying it could
 * possibly help. That is the same reason {@code PipelineError} is sealed at
 * all, applied to the second question every error raises.
 *
 * <p>Bolum 30.5 lists ten cases and seven exist. {@code QuotaExceeded} arrived
 * with Adim 2.7 and did exactly what this design is for: adding it failed both
 * this switch and {@code ErrorPresenter} to compile, so nobody could add a
 * failure without deciding what the user is told and whether repeating it
 * could help. The remaining three — {@code EmbeddingUnavailable},
 * {@code FeatureRequiresAccount}, {@code RewriteValidationFailed} — arrive the
 * same way.
 */
public final class JobRetryPolicy {

    /** Bolum 30.5: exponential, and jittered so a shared outage does not synchronise. */
    private static final long BASE_MS = 1000;
    private static final long JITTER_MS = 1000;

    /**
     * Long enough that a provider outage is not hammered, short enough that a
     * user watching the screen is not abandoned. Bolum 30.5's formula reaches
     * it at eight attempts; the retry budget is three.
     */
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private JobRetryPolicy() {
    }

    /**
     * @return whether another attempt could plausibly succeed. Note what this
     *         is not: it is not "was this the user's fault". A compilation
     *         failure is nobody's fault and is retried; a profile with no
     *         atoms in it is nobody's fault either and is not, because the
     *         second attempt reads the same empty profile.
     */
    public static boolean isRetryable(PipelineError error) {
        return switch (error) {
            // The chain ran out of providers, or TeX was not there. Both are
            // about the world outside, and the world outside changes.
            case PipelineError.AllProvidersUnavailable ignored -> true;
            case PipelineError.CompilationFailed ignored -> true;

            // All four are decided by inputs that will not have changed by the
            // time the next attempt reads them.
            case PipelineError.InsufficientProfile ignored -> false;
            case PipelineError.UnparseableJobDescription ignored -> false;
            case PipelineError.ConflictingPreferences ignored -> false;
            case PipelineError.PageLimitExceeded ignored -> false;

            // The next attempt reads the same counter. A retry budget spent
            // against a limit is three failures instead of one.
            case PipelineError.QuotaExceeded ignored -> false;
        };
    }

    /**
     * How long before the next attempt (Bolum 30.5).
     *
     * @param attempts how many have been made, which the claim has already
     *                 incremented — so the wait after the first failure is
     *                 about two seconds, not one
     * @param random   a parameter so the jitter can be pinned in a test. The
     *                 jitter itself is not decoration: without it every job
     *                 that failed to the same outage comes back at the same
     *                 instant and fails together again.
     */
    public static Duration backoff(int attempts, RandomGenerator random) {
        if (attempts < 1) {
            throw new IllegalArgumentException("A retry follows an attempt, got " + attempts);
        }
        // Capped before the shift rather than after: 2^attempts overflows a
        // long at 63, and a job that had been reclaimed enough times would
        // come back with a negative delay and run immediately, forever.
        int exponent = Math.min(attempts, 20);
        long millis = (1L << exponent) * BASE_MS + random.nextLong(JITTER_MS);
        return Duration.ofMillis(Math.min(millis, MAX_BACKOFF.toMillis()));
    }
}
