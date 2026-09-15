package com.mustafatetik.atomcv.llm.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One circuit breaker per vendor, in front of Bolum 27.3's chain walk.
 *
 * <p><strong>What it buys.</strong> The chain already moves on when a provider
 * is rate limited or down, so an outage was never a failed generation — it was
 * a slow one. Every request paid the full timeout at the dead vendor before
 * asking the next, and with a 30s call timeout a single dark provider at the
 * head of the chain put half a minute on every generation in the product.
 * Bolum 5.1 names a circuit breaker for exactly this and it had never been
 * wired.
 *
 * <p><strong>Only transport failures open it.</strong> A schema mismatch is a
 * property of the prompt, not of the vendor — Bolum 27.3 says so, which is why
 * it retries in place rather than moving on. Counting one as a vendor failure
 * would take a healthy provider out of the chain over a bad prompt version,
 * and take it out for every prompt rather than the one that was wrong. The
 * split is {@link LlmFailure.Kind#tryNextProvider()}, the same predicate the
 * walk already turns on.
 *
 * <p><strong>Half-open is the reason this is a breaker and not a flag.</strong>
 * After the wait the next call is let through; if it answers, the vendor is
 * back with no operator involved. A latch somebody has to reset is how an
 * outage that ended on Sunday is still costing money on Wednesday.
 */
@Component
public class ProviderBreakers {

    private static final Logger log = LoggerFactory.getLogger(ProviderBreakers.class);

    /**
     * Bolum 48.3's LLM row, the part fallback rate cannot answer.
     *
     * <p>The fallback counter says a call went to a second vendor; it cannot
     * say the first one has been dark for an hour, because a deployment with
     * one key answers "primary" forever either way. This gauge is per vendor
     * and reads 1 while its breaker is refusing.
     */
    static final String BREAKER_OPEN = "llm.provider.breaker.open";

    private final CircuitBreakerRegistry registry;
    private final MeterRegistry meters;
    private final ConcurrentMap<String, AtomicInteger> openState = new ConcurrentHashMap<>();

    public ProviderBreakers(MeterRegistry meters) {
        this.meters = meters;
        this.registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                // Count-based rather than time-based: this chain is walked once
                // per generation, so a sixty-second window can hold two calls on
                // a quiet afternoon and a time-based rate over two calls is
                // noise. Ten is a number of *attempts*, which is what the
                // decision is actually about.
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                // Below this nothing opens, whatever the rate. The first call
                // after a restart failing is one data point, and one data point
                // is a 100% failure rate.
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50.0f)
                // Long enough that a vendor's own rate-limit window has a chance
                // to roll over, short enough that an outage ending is noticed
                // within a generation or two rather than a deploy.
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build());
    }

    /**
     * Whether this vendor is worth asking.
     *
     * <p>False means the breaker is open — the vendor is known down and the
     * walk should move on without paying the timeout. It is deliberately NOT
     * folded into {@link LlmProvider#isAvailable()}: that method answers "is
     * there a key", which is a fact about configuration, and Bolum 27.3 skips
     * a keyless provider <em>without counting it as tried</em>. A vendor whose
     * breaker is open was configured and is failing, and belongs in {@code
     * tried} — otherwise {@code AllProvidersUnavailable} names an empty list
     * during precisely the outage it exists to describe.
     */
    public boolean isWorthAsking(String providerId) {
        return breaker(providerId).tryAcquirePermission();
    }

    /** Feeds one attempt's result back, so the window has something to count. */
    public void record(String providerId, LlmFailure.Kind failure, long elapsedNanos) {
        CircuitBreaker breaker = breaker(providerId);
        if (failure == null) {
            breaker.onSuccess(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        } else if (failure.tryNextProvider()) {
            breaker.onError(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS,
                    new ProviderUnreachable(providerId, failure));
        } else {
            // A schema mismatch: the vendor answered, and answered promptly.
            // Recording it as a success is not a fiction — the question this
            // window asks is "can this vendor be reached", and it could.
            breaker.onSuccess(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
        publishState(providerId, breaker);
    }

    private CircuitBreaker breaker(String providerId) {
        return registry.circuitBreaker(providerId);
    }

    private void publishState(String providerId, CircuitBreaker breaker) {
        boolean open = breaker.getState() == CircuitBreaker.State.OPEN
                || breaker.getState() == CircuitBreaker.State.FORCED_OPEN;
        AtomicInteger state = openState.computeIfAbsent(providerId, id -> {
            AtomicInteger value = new AtomicInteger();
            meters.gauge(BREAKER_OPEN,
                    java.util.List.of(io.micrometer.core.instrument.Tag.of(
                            "provider", id.toLowerCase(Locale.ROOT))),
                    value, AtomicInteger::doubleValue);
            return value;
        });
        int was = state.getAndSet(open ? 1 : 0);
        if (was == 0 && open) {
            // Absolute rule 4 is not at risk here and the line still carries no
            // content: a provider id and a state.
            log.warn("Circuit opened for provider '{}' -- skipping it until it is probed again",
                    providerId);
        } else if (was == 1 && !open) {
            log.info("Circuit closed for provider '{}'", providerId);
        }
    }

    /**
     * What the breaker counts, and it never leaves this class.
     *
     * <p>Resilience4j records failures as throwables; the chain records them as
     * {@link LlmFailure}. This is the adapter between the two and nothing
     * catches it — no stack trace is filled in, because the only thing anyone
     * would read it for is already in the warn line above.
     */
    private static final class ProviderUnreachable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ProviderUnreachable(String providerId, LlmFailure.Kind kind) {
            super(providerId + ": " + kind, null, false, false);
        }
    }
}
