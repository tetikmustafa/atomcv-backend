package com.mustafatetik.atomcv.llm.gateway;

/**
 * What one provider call came back with.
 *
 * <p>Separate from {@code Result} because its failure type is
 * {@link LlmFailure} rather than a {@code PipelineError}: a single provider's
 * refusal has no code in the error catalogue and is never shown to anyone. The
 * chain converts — {@code ProviderChain} returns a {@code Result}, and the only
 * error it can produce is {@code AllProvidersUnavailable}.
 *
 * <p>Sealed, so that handling a provider call is an exhaustive switch.
 */
public sealed interface LlmOutcome<T> permits LlmOutcome.Answered, LlmOutcome.Failed {

    record Answered<T>(LlmResponse<T> response) implements LlmOutcome<T> {
    }

    record Failed<T>(LlmFailure failure) implements LlmOutcome<T> {
    }

    static <T> LlmOutcome<T> answered(LlmResponse<T> response) {
        return new Answered<>(response);
    }

    static <T> LlmOutcome<T> failed(LlmFailure failure) {
        return new Failed<>(failure);
    }

    default boolean isFailed() {
        return this instanceof Failed<T>;
    }
}
