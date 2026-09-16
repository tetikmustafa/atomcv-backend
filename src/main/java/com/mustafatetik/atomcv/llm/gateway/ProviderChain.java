package com.mustafatetik.atomcv.llm.gateway;

import com.mustafatetik.atomcv.llm.telemetry.LlmInvocationEvent;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The fallback chain.
 *
 * <p>Walks the tier's providers in order and returns the first answer. What
 * separates it from a retry loop is the two ways a call can fail: a vendor
 * that is rate limited or down is a reason to ask someone else, and an answer
 * that did not fit the schema is not — the next vendor would fail the same
 * way, because the schema is a property of the prompt. That distinction lives
 * on {@link LlmFailure.Kind} rather than in an {@code if} here.
 *
 * <p>This is where {@code LlmOutcome} becomes {@code Result}: a single
 * provider's failure has no code in the catalogue and never reaches a user,
 * while the chain running out does — as
 * {@link PipelineError.AllProvidersUnavailable}.
 */
@Component
public class ProviderChain {

    private static final Logger log = LoggerFactory.getLogger(ProviderChain.class);

    private final Map<String, LlmProvider> providers;
    private final LlmProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Optional<AnswerRecorder> recorder;
    private final io.micrometer.core.instrument.MeterRegistry meters;
    private final ProviderBreakers breakers;

    public ProviderChain(List<LlmProvider> providers, LlmProperties properties,
                         ApplicationEventPublisher events, Clock clock,
                         Optional<AnswerRecorder> recorder,
                         io.micrometer.core.instrument.MeterRegistry meters,
                         ProviderBreakers breakers) {
        this.providers = providers.stream().collect(LinkedHashMap::new,
                (map, provider) -> map.put(provider.id(), provider), Map::putAll);
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        this.recorder = recorder;
        this.meters = meters;
        this.breakers = breakers;
    }

    /**
     * The provider fallback rate.
     *
     * <p>One counter with a position tag rather than two counters: a rate
     * needs a denominator, and the denominator here is every answer the chain
     * produced -- which is what the two tag values add up to. Counted at the
     * answer rather than at the attempt, because an attempt that failed on a
     * key nobody configured is not a fallback (those are skipped without being
     * counted), and a rate built on attempts would climb every time a
     * deployment ran with fewer providers than the chain names.
     */
    static final String CHAIN_ANSWERS = "llm.chain.answers";

    /** The walk that ran out of providers, which is the other end of the same rate. */
    static final String CHAIN_EXHAUSTED = "llm.chain.exhausted";

    public <T> Result<LlmResponse<T>> call(StructuredRequest<T> request) {
        var tried = new ArrayList<String>();
        // Whether the walk ran out of providers because they were slow rather
        // than because they were down. Only here can that be told apart: the
        // kinds are gone by the time the error reaches a handler, and the
        // difference is what lets ingestion answer "try again" instead of
        // "the model vendors are unreachable". A vendor skipped for a missing
        // key or an open breaker never answers the question either way.
        boolean sawFailure = false;
        boolean everyFailureTimedOut = true;

        for (String providerId : properties.chainFor(request.preferredTier())) {
            var provider = providers.get(providerId);
            if (provider == null) {
                // A chain naming an adapter that does not exist yet is an
                // ordinary state. Loud enough to catch a typo, not fatal.
                log.warn("Chain for {} names unknown provider '{}'",
                        request.preferredTier(), providerId);
                continue;
            }
            // No key means silently skipped, and *not* counted as tried. A
            // deployment with one key out of five is the normal case, and
            // reporting four outages for it would be a lie.
            if (!provider.isAvailable()) {
                continue;
            }

            tried.add(providerId);

            // The circuit breaker. Counted as tried and then skipped: this
            // vendor is configured and known to be failing, which is part of
            // why the walk will run out, and leaving it out would hand a user
            // AllProvidersUnavailable([]) in the middle of an outage.
            if (!breakers.isWorthAsking(providerId)) {
                continue;
            }

            var outcome = attempt(provider, request);
            if (outcome instanceof LlmOutcome.Answered<T> answered) {
                // The recording run, and the only place the answer and the
                // request that earned it are both in scope. Absent in every
                // profile but local-record.
                recorder.ifPresent(r -> r.record(request, answered.response().data()));
                meters.counter(CHAIN_ANSWERS,
                        "tier", request.preferredTier().name().toLowerCase(java.util.Locale.ROOT),
                        "position", tried.size() == 1 ? "primary" : "fallback").increment();
                return Result.ok(answered.response());
            }

            var failure = ((LlmOutcome.Failed<T>) outcome).failure();
            sawFailure = true;
            everyFailureTimedOut &= failure.kind() == LlmFailure.Kind.TIMEOUT;
            if (!failure.kind().tryNextProvider()) {
                // Asking the next vendor would buy the same answer at another
                // price. Stop the walk.
                log.warn("Chain stopped at {} for prompt {}: {}",
                        providerId, request.promptRef(), failure.kind());
                break;
            }
        }

        meters.counter(CHAIN_EXHAUSTED,
                "tier", request.preferredTier().name().toLowerCase(java.util.Locale.ROOT))
                .increment();
        return Result.err(new PipelineError.AllProvidersUnavailable(
                tried, sawFailure && everyFailureTimedOut));
    }

    /**
     * One provider, with the same-provider retry for a schema mismatch. A
     * model that wandered once often lands the second time; a prompt whose
     * schema is wrong fails every time, which is why the count is small and
     * configured.
     */
    private <T> LlmOutcome<T> attempt(LlmProvider provider, StructuredRequest<T> request) {
        long startedAt = System.nanoTime();
        LlmOutcome<T> outcome = timed(provider, request);
        for (int retry = 0; retry < properties.schemaRetries()
                && isSchemaMismatch(outcome); retry++) {
            outcome = timed(provider, request);
        }
        // One permission was taken and one result is fed back, whatever the
        // retry loop did in between: a schema retry is the same visit to the
        // same vendor, and counting it twice would halve the window a decision
        // is made on. Recorded here rather than at the call site because this
        // is the method that owns the loop.
        breakers.record(provider.id(),
                outcome instanceof LlmOutcome.Failed<T> failed ? failed.failure().kind() : null,
                System.nanoTime() - startedAt);
        return outcome;
    }

    private <T> boolean isSchemaMismatch(LlmOutcome<T> outcome) {
        return outcome instanceof LlmOutcome.Failed<T> failed
                && failed.failure().kind() == LlmFailure.Kind.SCHEMA_MISMATCH;
    }

    /** Every call is counted, the failures included. */
    private <T> LlmOutcome<T> timed(LlmProvider provider, StructuredRequest<T> request) {
        long startedAt = System.nanoTime();
        LlmOutcome<T> outcome = provider.callStructured(request);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        events.publishEvent(switch (outcome) {
            case LlmOutcome.Answered<T> answered ->
                    LlmInvocationEvent.succeeded(request, answered.response(), clock.instant());
            case LlmOutcome.Failed<T> failed -> LlmInvocationEvent.failed(request,
                    provider.id(), properties.modelFor(provider.id()),
                    outcomeOf(failed.failure()), elapsedMs, clock.instant());
        });
        return outcome;
    }

    private static LlmInvocationEvent.Outcome outcomeOf(LlmFailure failure) {
        return failure.kind() == LlmFailure.Kind.SCHEMA_MISMATCH
                ? LlmInvocationEvent.Outcome.SCHEMA_ERROR
                : LlmInvocationEvent.Outcome.PROVIDER_ERROR;
    }
}
