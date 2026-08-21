package com.mustafatetik.atomcv.llm.providers;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where OpenRouter is and what it is asked for (Bolum 27.2).
 *
 * <p>The key comes from the environment and has no default (absolute rule 5).
 * An empty one is not an error: Bolum 27.3 skips a provider without a key
 * silently, which is what lets one chain definition serve a deployment that
 * has configured one vendor and a deployment that has configured five.
 *
 * @param baseUrl          overridable so a test can point it at a local server
 * @param apiKey           {@code OPENROUTER_API_KEY}; blank means unconfigured
 * @param structuredOutput which of Bolum 27.2's two mechanisms to use
 */
@ConfigurationProperties(prefix = "atomcv.llm.openrouter")
public record OpenRouterProperties(
        String baseUrl, String apiKey, StructuredOutput structuredOutput) {

    /**
     * Bolum 27.2 gives OpenRouter two ways to be held to a schema, and says to
     * fall back "if unsupported". Which one a model supports is a fact about
     * that model rather than something the response reliably says, so it is
     * configuration rather than detection — guessing from an error string
     * would silently downgrade every failure into the weaker mode.
     */
    public enum StructuredOutput {

        /** {@code response_format: json_schema}. The provider enforces it. */
        JSON_SCHEMA,

        /** {@code json_object} with the schema in the prompt. Weaker: the model may wander. */
        JSON_OBJECT
    }

    public OpenRouterProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://openrouter.ai/api/v1"
                : baseUrl;
        apiKey = apiKey == null ? "" : apiKey.trim();
        structuredOutput = structuredOutput == null ? StructuredOutput.JSON_SCHEMA
                : structuredOutput;
    }

    public boolean hasKey() {
        return !apiKey.isEmpty();
    }
}
