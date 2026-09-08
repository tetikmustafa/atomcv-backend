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
 * @param dataCollection   whether a provider that may keep the prompt for
 *                         training is allowed to serve it. **Denied by
 *                         default**: the prompt is somebody's CV, and this is
 *                         the one setting that decides whether it can become
 *                         training data. Relaxing it is a deployment's choice
 *                         and a line in EK C.1's provider list, not a default
 * @param only             the provider slugs allowed to serve a call, empty for
 *                         "whatever the broker routes to". A broker's routing
 *                         is invisible in the answer: the same model is served
 *                         here by OpenAI, Azure in two regions and Amazon
 *                         Bedrock, which is three more names in the
 *                         sub-processor list and a price between 2 and 5.50 per
 *                         million. Naming one makes both knowable, at the cost
 *                         of the fallbacks inside the broker
 * @param zeroDataRetention restrict to endpoints that keep nothing at all.
 *                         **Off by default and deliberately**: which endpoints
 *                         qualify is not in the model's own metadata, so
 *                         turning it on blind can leave a request with no
 *                         eligible provider — measure it against the account
 *                         before shipping it
 */
@ConfigurationProperties(prefix = "atomcv.llm.openrouter")
public record OpenRouterProperties(
        String baseUrl, String apiKey, StructuredOutput structuredOutput,
        DataCollection dataCollection, java.util.List<String> only,
        boolean zeroDataRetention) {

    /** OpenRouter's `provider.data_collection`, verbatim. */
    public enum DataCollection {

        /** Any provider, including one that may train on the prompt. */
        ALLOW,

        /** Only providers that do not keep it. The default here. */
        DENY;

        /** The wire spelling: lowercase, which is what the field takes. */
        public String wireValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

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
        dataCollection = dataCollection == null ? DataCollection.DENY : dataCollection;
        only = only == null ? java.util.List.of() : java.util.List.copyOf(only);
    }

    /** Whether anything has to be said about routing at all. */
    public boolean restrictsRouting() {
        return dataCollection == DataCollection.DENY || !only.isEmpty() || zeroDataRetention;
    }

    public boolean hasKey() {
        return !apiKey.isEmpty();
    }
}
