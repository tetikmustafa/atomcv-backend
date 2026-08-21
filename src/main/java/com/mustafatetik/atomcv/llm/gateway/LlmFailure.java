package com.mustafatetik.atomcv.llm.gateway;

/**
 * Why one provider did not answer, and what the chain should do next
 * (Bolum 27.3).
 *
 * <p>This is deliberately <em>not</em> a {@code PipelineError}. Bolum 27.3's
 * snippet returns a failed provider call straight to the caller, which would
 * make it a user-facing error — but the catalogue in EK D.6 publishes only two
 * LLM codes, {@code ALL_PROVIDERS_UNAVAILABLE} and {@code EMBEDDING_UNAVAILABLE},
 * and neither describes one provider's 429. A single provider's failure is a
 * fact about the chain's walk, not a message to a user, so it stays inside this
 * module and only {@code AllProvidersUnavailable} escapes.
 *
 * <p>No provider text is carried: {@code detail} is for a developer's log and
 * is written by the adapter, never taken from the model's output (absolute
 * rule 4).
 *
 * @param kind     what happened, and therefore where the chain goes next
 * @param provider which adapter this was
 * @param detail   a short developer-facing note, never user or model content
 */
public record LlmFailure(Kind kind, String provider, String detail) {

    public LlmFailure {
        if (kind == null) {
            throw new IllegalArgumentException("A failure names its kind");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("A failure names its provider");
        }
        detail = detail == null ? "" : detail;
    }

    public enum Kind {

        /** Rate limited. The next provider in the chain is worth trying. */
        RATE_LIMITED(true),

        /** The provider answered 5xx. Someone else may be up. */
        SERVER_ERROR(true),

        /** No answer inside {@link StructuredRequest#timeout()}. */
        TIMEOUT(true),

        /** Could not be reached at all — DNS, connection, TLS. */
        UNREACHABLE(true),

        /**
         * The answer did not match the schema, or did not parse.
         *
         * <p>Bolum 27.3: this one does <em>not</em> advance the chain. A schema
         * the model could not satisfy is a property of the prompt, and the next
         * provider will fail the same way; the retry belongs on the same
         * provider.
         */
        SCHEMA_MISMATCH(false),

        /** The provider rejected the request itself — bad key, bad model id. */
        REQUEST_REJECTED(false);

        private final boolean tryNextProvider;

        Kind(boolean tryNextProvider) {
            this.tryNextProvider = tryNextProvider;
        }

        /** Bolum 27.3: 429/5xx/timeout advance the chain, a schema error does not. */
        public boolean tryNextProvider() {
            return tryNextProvider;
        }
    }
}
