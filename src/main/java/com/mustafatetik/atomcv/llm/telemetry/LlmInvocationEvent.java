package com.mustafatetik.atomcv.llm.telemetry;

import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import java.time.Instant;

/**
 * One provider call, as something to count (Bolum 27.5).
 *
 * <p>Published by the chain whether the call succeeded or not, because the
 * failures are the half that says whether a provider is worth its place in the
 * order.
 *
 * <p><strong>Carries no content.</strong> Not the prompt, not the answer — the
 * table it lands in says so in the schema itself (V1,
 * {@code llm_invocations}), and absolute rule 4 says so everywhere else. What
 * is here is a shape: which prompt at which version, who answered, what it
 * cost.
 *
 * @param promptId      the prompt's directory name
 * @param promptVersion the version that ran, which an experiment can vary per
 *                      user (Bolum 53.3)
 * @param provider      the adapter that answered, or the one that failed
 * @param model         the model id asked for
 * @param outcome       one of the four values the column allows
 * @param inputTokens   zero when the call failed before being billed
 * @param outputTokens  as above
 * @param cachedTokens  the discounted subset of {@code inputTokens}
 * @param latencyMs     measured around the call
 * @param occurredAt    when, from the injected clock
 */
public record LlmInvocationEvent(
        String promptId,
        String promptVersion,
        String provider,
        String model,
        Outcome outcome,
        int inputTokens,
        int outputTokens,
        int cachedTokens,
        long latencyMs,
        Instant occurredAt) {

    /** The values {@code llm_invocations.outcome} allows, verbatim from V1. */
    public enum Outcome {
        SUCCESS,
        SCHEMA_ERROR,
        VALIDATION_FAILED,
        PROVIDER_ERROR
    }

    public static LlmInvocationEvent succeeded(
            StructuredRequest<?> request, LlmResponse<?> response, Instant at) {
        return new LlmInvocationEvent(
                request.promptId(), request.promptVersion(),
                response.provider(), response.model(), Outcome.SUCCESS,
                response.inputTokens(), response.outputTokens(), response.cachedTokens(),
                response.latencyMs(), at);
    }

    public static LlmInvocationEvent failed(
            StructuredRequest<?> request, String provider, String model,
            Outcome outcome, long latencyMs, Instant at) {
        return new LlmInvocationEvent(
                request.promptId(), request.promptVersion(), provider, model, outcome,
                0, 0, 0, latencyMs, at);
    }
}
