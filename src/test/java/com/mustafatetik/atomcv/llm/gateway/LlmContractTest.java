package com.mustafatetik.atomcv.llm.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The gateway's value types, held to what Bolum 27.1 and Bolum 27.3 say about
 * them. These run without a provider: what is under test is the shape every
 * adapter in Adim 2.2 will be written against.
 */
class LlmContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    // ── Bolum 27.3: which failures advance the chain ──────────────────────

    @Test
    void rateLimitsAndOutagesAdvanceTheChain() {
        assertThat(LlmFailure.Kind.RATE_LIMITED.tryNextProvider()).isTrue();
        assertThat(LlmFailure.Kind.SERVER_ERROR.tryNextProvider()).isTrue();
        assertThat(LlmFailure.Kind.TIMEOUT.tryNextProvider()).isTrue();
        assertThat(LlmFailure.Kind.UNREACHABLE.tryNextProvider()).isTrue();
    }

    /**
     * The other half of Bolum 27.3, and the one that costs money when it is
     * wrong: a schema the model could not satisfy is a property of the prompt,
     * so walking the chain buys four more failed calls at four more vendors.
     */
    @Test
    void aSchemaMismatchStaysOnTheSameProvider() {
        assertThat(LlmFailure.Kind.SCHEMA_MISMATCH.tryNextProvider()).isFalse();
        assertThat(LlmFailure.Kind.REQUEST_REJECTED.tryNextProvider()).isFalse();
    }

    @Test
    void everyFailureKindHasDecidedWhereTheChainGoes() {
        // A kind added without a decision would default to one of the two
        // silently; this fails the moment the enum grows past what is asserted
        // above.
        assertThat(Arrays.stream(LlmFailure.Kind.values())
                .filter(LlmFailure.Kind::tryNextProvider).count()).isEqualTo(4);
        assertThat(LlmFailure.Kind.values()).hasSize(6);
    }

    // ── Bolum 27.4: the cached subset is priced apart ─────────────────────

    @Test
    void cachedTokensAreASubsetOfTheInputAndNotBilledAsFresh() {
        var response = new LlmResponse<>("data", "openrouter", "some-model", 1000, 200, 800, 900);

        assertThat(response.billedInputTokens()).isEqualTo(200);
    }

    /**
     * A provider that reports more cached tokens than input tokens would
     * otherwise produce a negative bill, which is a silently wrong cost rather
     * than a loud one.
     */
    @Test
    void aCachedCountLargerThanTheInputIsClampedRatherThanTrusted() {
        var response = new LlmResponse<>("data", "gemini", "some-model", 100, 10, 4000, 50);

        assertThat(response.cachedTokens()).isEqualTo(100);
        assertThat(response.billedInputTokens()).isZero();
    }

    @Test
    void negativeCountsFromAProviderNeverReachTheCostCounter() {
        var response = new LlmResponse<>("data", "deepseek", "some-model", -5, -5, -5, -5);

        assertThat(response.inputTokens()).isZero();
        assertThat(response.outputTokens()).isZero();
        assertThat(response.cachedTokens()).isZero();
        assertThat(response.latencyMs()).isZero();
    }

    @Test
    void aResponseAlwaysNamesWhoAnsweredIt() {
        assertThatThrownBy(() -> new LlmResponse<>("data", " ", "m", 1, 1, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LlmResponse<>("data", "openrouter", "", 1, 1, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Bolum 27.1 / 53.1: the request carries its prompt version ─────────

    @Test
    void theRequestNamesThePromptAndItsVersionForTelemetry() {
        assertThat(request().promptRef()).isEqualTo("job_analysis:v1");
    }

    @Test
    void anAbsentSystemPromptIsEmptyRatherThanNull() {
        var request = new StructuredRequest<>("job_analysis", "v1", null, "posting",
                schema(), String.class, ModelTier.CHEAP, Duration.ofSeconds(30));

        assertThat(request.systemPrompt()).isEmpty();
    }

    @Test
    void aCallWithoutATimeoutIsRefusedRatherThanLeftToHang() {
        assertThatThrownBy(() -> new StructuredRequest<>("job_analysis", "v1", "", "posting",
                schema(), String.class, ModelTier.CHEAP, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEmptySchemaDocumentIsRefused() {
        assertThatThrownBy(() -> new JsonSchema("job_analysis", JSON.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── The outcome type ─────────────────────────────────────────────────

    @Test
    void anOutcomeIsEitherAnsweredOrFailed() {
        LlmOutcome<String> answered = LlmOutcome.answered(
                new LlmResponse<>("data", "openrouter", "m", 1, 1, 0, 1));
        LlmOutcome<String> failed = LlmOutcome.failed(
                new LlmFailure(LlmFailure.Kind.TIMEOUT, "openrouter", "45s elapsed"));

        assertThat(answered.isFailed()).isFalse();
        assertThat(failed.isFailed()).isTrue();
    }

    @Test
    void aFailureWithoutADetailIsEmptyRatherThanNull() {
        var failure = new LlmFailure(LlmFailure.Kind.SERVER_ERROR, "openai", null);

        assertThat(failure.detail()).isEmpty();
    }

    private static StructuredRequest<String> request() {
        return new StructuredRequest<>("job_analysis", "v1", "system", "posting",
                schema(), String.class, ModelTier.CHEAP, Duration.ofSeconds(30));
    }

    private static JsonSchema schema() {
        return new JsonSchema("job_analysis",
                JSON.createObjectNode().put("type", "object"));
    }
}
