package com.mustafatetik.atomcv.llm.gateway;

import java.time.Duration;

/**
 * One structured call, provider-independent (Bolum 27.1).
 *
 * <p>Everything an adapter needs and nothing about how a particular vendor
 * asks for it: the five mechanisms in Bolum 27.2 are the adapters' business,
 * not the caller's.
 *
 * <p>{@code promptId} and {@code promptVersion} travel with the request rather
 * than being looked up at the edge, because telemetry records the version that
 * actually ran (Bolum 27.5) and an A/B experiment can hand two calls different
 * versions of the same prompt (Bolum 53.3).
 *
 * @param promptId      which prompt, as its directory name under
 *                      {@code resources/prompts} (Bolum 53.1)
 * @param promptVersion which version of it ran, e.g. {@code v1}
 * @param systemPrompt  held constant across calls so that provider-side prompt
 *                      caching can discount it (Bolum 27.4)
 * @param userPrompt    the varying part
 * @param outputSchema  the shape the answer must take
 * @param resultType    what the answer is parsed into
 * @param preferredTier which chain to walk (Bolum 27.3)
 * @param timeout       per provider, not for the chain as a whole
 */
public record StructuredRequest<T>(
        String promptId,
        String promptVersion,
        String systemPrompt,
        String userPrompt,
        JsonSchema outputSchema,
        Class<T> resultType,
        ModelTier preferredTier,
        Duration timeout) {

    public StructuredRequest {
        requireText(promptId, "promptId");
        requireText(promptVersion, "promptVersion");
        requireText(userPrompt, "userPrompt");
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        if (outputSchema == null) {
            throw new IllegalArgumentException("A structured call needs an output schema");
        }
        if (resultType == null) {
            throw new IllegalArgumentException("A structured call needs a result type");
        }
        if (preferredTier == null) {
            throw new IllegalArgumentException("A structured call needs a tier");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("A structured call needs a positive timeout");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    /**
     * How the prompt is named in telemetry and in the fixture key — never the
     * prompt text itself (absolute rule 4).
     */
    public String promptRef() {
        return promptId + ":" + promptVersion;
    }
}
