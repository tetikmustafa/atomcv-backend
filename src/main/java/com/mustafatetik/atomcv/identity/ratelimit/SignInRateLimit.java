package com.mustafatetik.atomcv.identity.ratelimit;

import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import org.springframework.stereotype.Component;

/**
 * Bolum 40.5's three layers, applied to asking for a sign-in link.
 *
 * <p><strong>They are not applied together, and the split is the point.</strong>
 * The caller's layers run before the challenge; the address layer runs after
 * it. Three requests are enough to exhaust an address's window, so a limiter
 * reachable without proving you are a person is a way to lock a stranger out
 * of their own account — the brake becomes the attack. Turnstile in front of
 * that layer prices it out of reach; in front of the other two it would only
 * buy a bot a free round trip to Cloudflare on our budget.
 *
 * <p><strong>A refusal is a 429 and says when, and neither leaks anything
 * Bolum 40.4 protects.</strong> The address layer only ever refuses a caller
 * who already spent that window themselves, so the answer tells them what they
 * did, not whether the address has an account.
 */
@Component
public class SignInRateLimit {

    private final RateLimiter limiter;
    private final RateLimitProperties limits;

    SignInRateLimit(RateLimiter limiter, RateLimitProperties limits) {
        this.limiter = limiter;
        this.limits = limits;
    }

    /**
     * The caller's two layers: this address, and the deployment as a whole.
     *
     * <p>The global layer is checked second on purpose. Both increment on
     * admission, and a request the IP layer would refuse should not spend a
     * slot out of everybody's allowance.
     *
     * @param ip the caller as {@link ClientIp} resolved it
     * @throws ApiException {@code RATE_LIMITED}, carrying when to retry
     */
    public void checkCaller(String ip) {
        enforce(limiter.check("ip", RateLimiter.subjectOf(ip),
                limits.perIp().limit(), limits.perIp().window()));
        enforce(limiter.check("global", "all",
                limits.global().limit(), limits.global().window()));
    }

    /**
     * The address's layer, which runs once a person has been proven.
     *
     * @param normalisedEmail the address exactly as the account lookup will use
     *                        it — trimmed and lowercased with {@code Locale.ROOT}.
     *                        Anything else buckets {@code A@x.com} apart from
     *                        {@code a@x.com} and the limit counts double.
     * @throws ApiException {@code RATE_LIMITED}, carrying when to retry
     */
    public void checkAddress(String normalisedEmail) {
        enforce(limiter.check("email", RateLimiter.subjectOf(normalisedEmail),
                limits.perEmail().limit(), limits.perEmail().window()));
    }

    /**
     * Which layer refused is not published.
     *
     * <p>One code, no scope: the sentence the user reads is the same either way
     * — wait, then try again — and naming the global layer would tell an
     * attacker their traffic is landing. The layer reaches the operator through
     * {@link RateLimiter}'s log line instead.
     */
    private static void enforce(RateLimitDecision decision) {
        if (decision.allowed()) {
            return;
        }
        throw new ApiException(UserFacingError.with(ErrorCode.RATE_LIMITED)
                .param("resetsAt", decision.resetsAt())
                .build());
    }
}
