package com.mustafatetik.atomcv.identity.ratelimit;

import java.time.Instant;

/**
 * What one layer of Bolum 40.5 answered.
 *
 * <p>{@code resetsAt} is present on a refusal and only on a refusal: it is the
 * moment the oldest request in the window falls out of it, which is the
 * earliest a retry can succeed. An allowed request has no such moment, and a
 * field that would have to carry one anyway invites a caller to publish a
 * meaningless number.
 *
 * @param allowed  whether the request may proceed
 * @param resetsAt when a refused caller may try again; {@code null} when allowed
 */
public record RateLimitDecision(boolean allowed, Instant resetsAt) {

    /** Named for the act, because {@code allowed()} is the accessor. */
    static RateLimitDecision admit() {
        return new RateLimitDecision(true, null);
    }

    static RateLimitDecision refusedUntil(Instant resetsAt) {
        return new RateLimitDecision(false, resetsAt);
    }

    public RateLimitDecision {
        if (allowed && resetsAt != null) {
            throw new IllegalArgumentException(
                    "an allowed request has no moment to retry at");
        }
        if (!allowed && resetsAt == null) {
            throw new IllegalArgumentException(
                    "a refusal has to say when: Retry-After is derived from it");
        }
    }
}
