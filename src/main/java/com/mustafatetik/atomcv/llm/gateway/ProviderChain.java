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
 * The fallback chain (Bolum 27.3).
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

    public ProviderChain(List<LlmProvider> providers, LlmProperties properties,
                         ApplicationEventPublisher events, Clock clock,
                         Optional<AnswerRecorder> recorder) {
        this.providers = providers.stream().collect(LinkedHashMap::new,
                (map, provider) -> map.put(provider.id(), provider), Map::putAll);
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        this.recorder = recorder;
    }

    public <T> Result<LlmResponse<T>> call(StructuredRequest<T> request) {
        var tried = new ArrayList<String>();

        for (String providerId : properties.chainFor(request.preferredTier())) {
            var provider = providers.get(providerId);
            if (provider == null) {
                // A chain naming an adapter that does not exist yet is the
                // normal state while Adim 2.2 is being built out. Loud enough
                // to catch a typo, not fatal.
                log.warn("Chain for {} names unknown provider '{}'",
                        request.preferredTier(), providerId);
                continue;
            }
            // Bolum 27.3: no key means silently skipped, and *not* counted as
            // tried. A deployment with one key out of five is the normal case,
            // and reporting four outages for it would be a lie.
            if (!provider.isAvailable()) {
                continue;
            }

            tried.add(providerId);
            var outcome = attempt(provider, request);
            if (outcome instanceof LlmOutcome.Answered<T> answered) {
                // Bolum 54.2's recording run, and the only place the answer
                // and the request that earned it are both in scope. Absent in
                // every profile but local-record.
                recorder.ifPresent(r -> r.record(request, answered.response().data()));
                return Result.ok(answered.response());
            }

            var failure = ((LlmOutcome.Failed<T>) outcome).failure();
            if (!failure.kind().tryNextProvider()) {
                // Asking the next vendor would buy the same answer at another
                // price. Stop the walk.
                log.warn("Chain stopped at {} for prompt {}: {}",
                        providerId, request.promptRef(), failure.kind());
                break;
            }
        }

        return Result.err(new PipelineError.AllProvidersUnavailable(tried));
    }

    /**
     * One provider, with Bolum 27.3's same-provider retry for a schema
     * mismatch. A model that wandered once often lands the second time; a
     * prompt whose schema is wrong fails every time, which is why the count is
     * small and configured.
     */
    private <T> LlmOutcome<T> attempt(LlmProvider provider, StructuredRequest<T> request) {
        LlmOutcome<T> outcome = timed(provider, request);
        for (int retry = 0; retry < properties.schemaRetries()
                && isSchemaMismatch(outcome); retry++) {
            outcome = timed(provider, request);
        }
        return outcome;
    }

    private <T> boolean isSchemaMismatch(LlmOutcome<T> outcome) {
        return outcome instanceof LlmOutcome.Failed<T> failed
                && failed.failure().kind() == LlmFailure.Kind.SCHEMA_MISMATCH;
    }

    /** Bolum 27.5: every call is counted, the failures included. */
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
