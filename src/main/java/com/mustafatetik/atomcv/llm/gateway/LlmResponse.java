package com.mustafatetik.atomcv.llm.gateway;

/**
 * A structured answer, with what it cost to get (Bolum 27.1).
 *
 * <p>The token counts are here rather than looked up later because only the
 * adapter sees them: each provider reports usage in its own envelope, and by
 * the time the parsed value reaches a phase the envelope is gone. Telemetry
 * (Bolum 27.5) and the cost counter both read them from here.
 *
 * <p>{@code cachedTokens} is reported separately because it is priced
 * separately — a cached input token costs a fraction of a fresh one
 * (Bolum 27.4), and a cost computed without it overstates every call that a
 * constant system prompt made cheap.
 *
 * @param data         the parsed answer
 * @param provider     which adapter answered, for telemetry and for the
 *                     {@code tried} list a chain failure publishes
 * @param model        the model id that ran, as configured
 * @param inputTokens  prompt tokens the provider billed
 * @param outputTokens completion tokens the provider billed
 * @param cachedTokens the discounted subset of {@code inputTokens}; zero when
 *                     the provider does not report it
 * @param latencyMs    measured around the call, not read from the provider
 */
public record LlmResponse<T>(
        T data,
        String provider,
        String model,
        int inputTokens,
        int outputTokens,
        int cachedTokens,
        long latencyMs) {

    public LlmResponse {
        if (data == null) {
            throw new IllegalArgumentException("A response carries a value");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("A response names its provider");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("A response names its model");
        }
        inputTokens = Math.max(0, inputTokens);
        outputTokens = Math.max(0, outputTokens);
        cachedTokens = Math.max(0, Math.min(cachedTokens, inputTokens));
        latencyMs = Math.max(0, latencyMs);
    }

    /** Fresh input tokens — what {@code cachedTokens} was not. */
    public int billedInputTokens() {
        return inputTokens - cachedTokens;
    }
}
