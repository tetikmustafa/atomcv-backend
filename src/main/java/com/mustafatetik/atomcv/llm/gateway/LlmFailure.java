package com.mustafatetik.atomcv.llm.gateway;

/**
 * Why one provider did not answer, and what the chain should do next.
 *
 * <p>This is deliberately <em>not</em> a {@code PipelineError}. The snippet
 * returns a failed provider call straight to the caller, which would make it a
 * user-facing error — but the catalogue publishes only two LLM codes, {@code
 * ALL_PROVIDERS_UNAVAILABLE} and {@code EMBEDDING_UNAVAILABLE}, and neither
 * describes one provider's 429. A single provider's failure is a fact about
 * the chain's walk, not a message to a user, so it stays inside this module
 * and only {@code AllProvidersUnavailable} escapes.
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
         * <p>This one does <em>not</em> advance the chain. A schema the model
         * could not satisfy is a property of the prompt, and the next provider
         * will fail the same way; the retry belongs on the same provider.
         */
        SCHEMA_MISMATCH(false),

        /**
         * This provider refused, for a reason that is its own: a key it does
         * not accept (401/403), an account out of credit (402), a model id it
         * does not serve (404), or — at Gemini — a schema outside its subset.
         *
         * <p>Another vendor is a different key, a different balance and a
         * different schema dialect, so the chain walks on. Measured on
         * 2026-10-24: OpenRouter answered 402 to every call and Gemini 400, the
         * two were configured as each other's fallback, and each stopped the
         * chain where it stood — the second link existed and was never asked.
         */
        PROVIDER_REFUSED(true),

        /**
         * The request itself is malformed — a 400 or 422 from a vendor whose
         * dialect is plain JSON Schema, where the next vendor would read the
         * same body the same way.
         */
        REQUEST_REJECTED(false);

        private final boolean tryNextProvider;

        Kind(boolean tryNextProvider) {
            this.tryNextProvider = tryNextProvider;
        }

        /** 429/5xx/timeout advance the chain; a schema error does not. */
        public boolean tryNextProvider() {
            return tryNextProvider;
        }
    }
}
