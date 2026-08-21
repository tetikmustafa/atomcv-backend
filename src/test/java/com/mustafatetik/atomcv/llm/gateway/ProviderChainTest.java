package com.mustafatetik.atomcv.llm.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.telemetry.LlmInvocationEvent;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Bolum 27.3: the order, the skips, and the one failure that stops the walk. */
class ProviderChainTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-21T09:00:00Z"), ZoneOffset.UTC);

    private final List<LlmInvocationEvent> published = new ArrayList<>();

    @Test
    void theFirstProviderThatAnswersWins() {
        var chain = chain(List.of(answering("gemini"), answering("deepseek")));

        assertThat(ok(chain.call(request())).provider()).isEqualTo("gemini");
    }

    /**
     * Bolum 27.3: 429/5xx/timeout are reasons to ask someone else, and that is
     * the whole point of having a chain.
     */
    @Test
    void aRateLimitedProviderHandsOverToTheNext() {
        var chain = chain(List.of(
                failing("gemini", LlmFailure.Kind.RATE_LIMITED),
                answering("deepseek")));

        assertThat(ok(chain.call(request())).provider()).isEqualTo("deepseek");
    }

    /**
     * The other half, and the expensive one to get wrong: the next vendor
     * would fail the same way, because the schema is a property of the prompt.
     */
    @Test
    void aSchemaMismatchStopsTheWalkRatherThanPayingTheNextVendor() {
        var second = new CountingProvider(answering("deepseek"));
        var chain = chain(List.of(
                failing("gemini", LlmFailure.Kind.SCHEMA_MISMATCH), second));

        var error = err(chain.call(request()));

        assertThat(error.tried()).containsExactly("gemini");
        assertThat(second.calls()).isZero();
    }

    /**
     * Bolum 27.3 skips a provider with no key silently, and does not count it
     * as tried: five vendors in a chain on a deployment with one key is the
     * normal case, and reporting four outages for it would be a lie.
     */
    @Test
    void aProviderWithNoKeyIsSkippedAndNotReportedAsTried() {
        var chain = chain(List.of(
                unavailable("openai"),
                failing("gemini", LlmFailure.Kind.SERVER_ERROR)));

        assertThat(err(chain.call(request())).tried()).containsExactly("gemini");
    }

    @Test
    void aChainWithNothingConfiguredReportsAnEmptyTriedList() {
        var chain = chain(List.of(unavailable("openai"), unavailable("gemini")));

        assertThat(err(chain.call(request())).tried()).isEmpty();
    }

    /** A chain naming an adapter that is not built yet must not stop the walk. */
    @Test
    void anUnknownProviderIdIsSteppedOverRatherThanThrowing() {
        var chain = chain(List.of(answering("deepseek")),
                List.of("not_built_yet", "deepseek"), 0);

        assertThat(ok(chain.call(request())).provider()).isEqualTo("deepseek");
    }

    // ── The same-provider retry ───────────────────────────────────────────

    @Test
    void aSchemaMismatchIsRetriedOnTheSameProviderBeforeTheWalkStops() {
        var flaky = new FlakyProvider("gemini", 1);
        var chain = chain(List.of(flaky), List.of("gemini"), 1);

        assertThat(ok(chain.call(request())).provider()).isEqualTo("gemini");
        assertThat(flaky.calls()).isEqualTo(2);
    }

    @Test
    void aPromptWhoseSchemaIsWrongIsNotPaidForIndefinitely() {
        var always = new CountingProvider(failing("gemini", LlmFailure.Kind.SCHEMA_MISMATCH));
        var chain = chain(List.of(always), List.of("gemini"), 2);

        assertThat(chain.call(request()).isErr()).isTrue();
        assertThat(always.calls()).isEqualTo(3);
    }

    // ── Bolum 27.5: every call is counted, failures included ──────────────

    @Test
    void aSuccessfulCallIsRecorded() {
        chain(List.of(answering("gemini"))).call(request());

        assertThat(published).singleElement().satisfies(event -> {
            assertThat(event.outcome()).isEqualTo(LlmInvocationEvent.Outcome.SUCCESS);
            assertThat(event.promptId()).isEqualTo("job_analysis");
            assertThat(event.promptVersion()).isEqualTo("v1");
            assertThat(event.provider()).isEqualTo("gemini");
            assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-08-21T09:00:00Z"));
        });
    }

    /**
     * The failures are the half that says whether a provider is worth its
     * place in the order, so a chain that recovered still records the stop it
     * made on the way.
     */
    @Test
    void aFailedCallIsRecordedToo() {
        chain(List.of(failing("gemini", LlmFailure.Kind.RATE_LIMITED), answering("deepseek")))
                .call(request());

        assertThat(published).extracting(LlmInvocationEvent::outcome).containsExactly(
                LlmInvocationEvent.Outcome.PROVIDER_ERROR,
                LlmInvocationEvent.Outcome.SUCCESS);
    }

    @Test
    void aSchemaMismatchIsRecordedApartFromAProviderOutage() {
        chain(List.of(failing("gemini", LlmFailure.Kind.SCHEMA_MISMATCH))).call(request());

        assertThat(published).singleElement()
                .extracting(LlmInvocationEvent::outcome)
                .isEqualTo(LlmInvocationEvent.Outcome.SCHEMA_ERROR);
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private ProviderChain chain(List<LlmProvider> providers) {
        return chain(providers, providers.stream().map(LlmProvider::id).toList(), 0);
    }

    private ProviderChain chain(List<LlmProvider> providers, List<String> order, int retries) {
        var properties = new LlmProperties(
                Map.of(ModelTier.CHEAP, order), Map.of("gemini", "some-model"),
                Duration.ofSeconds(30), retries);
        return new ProviderChain(providers, properties,
                event -> published.add((LlmInvocationEvent) event), CLOCK);
    }

    private static LlmResponse<String> ok(Result<LlmResponse<String>> result) {
        return ((Result.Ok<LlmResponse<String>>) result).value();
    }

    private static PipelineError.AllProvidersUnavailable err(Result<LlmResponse<String>> result) {
        return (PipelineError.AllProvidersUnavailable)
                ((Result.Err<LlmResponse<String>>) result).error();
    }

    private static StructuredRequest<String> request() {
        return new StructuredRequest<>("job_analysis", "v1", "system", "a posting",
                new JsonSchema("job_analysis", JSON.createObjectNode().put("type", "string")),
                String.class, ModelTier.CHEAP, Duration.ofSeconds(30));
    }

    // ── stub providers ────────────────────────────────────────────────────

    private static LlmProvider answering(String id) {
        return new StubProvider(id, true, request ->
                LlmOutcome.answered(new LlmResponse<>("answer", id, "some-model", 10, 5, 0, 12)));
    }

    private static LlmProvider failing(String id, LlmFailure.Kind kind) {
        return new StubProvider(id, true, request ->
                LlmOutcome.failed(new LlmFailure(kind, id, "stubbed")));
    }

    private static LlmProvider unavailable(String id) {
        return new StubProvider(id, false, request -> {
            throw new AssertionError("An unavailable provider must never be called");
        });
    }

    private record StubProvider(
            String id, boolean available,
            Function<StructuredRequest<?>, LlmOutcome<?>> answer) implements LlmProvider {

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public ModelTier tier() {
            return ModelTier.CHEAP;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> LlmOutcome<T> callStructured(StructuredRequest<T> request) {
            return (LlmOutcome<T>) answer.apply(request);
        }
    }

    /** Counts what reached it, so "the walk stopped" can be asserted directly. */
    private static final class CountingProvider implements LlmProvider {
        private final LlmProvider delegate;
        private final AtomicInteger calls = new AtomicInteger();

        CountingProvider(LlmProvider delegate) {
            this.delegate = delegate;
        }

        int calls() {
            return calls.get();
        }

        @Override
        public String id() {
            return delegate.id();
        }

        @Override
        public boolean isAvailable() {
            return delegate.isAvailable();
        }

        @Override
        public ModelTier tier() {
            return delegate.tier();
        }

        @Override
        public <T> LlmOutcome<T> callStructured(StructuredRequest<T> request) {
            calls.incrementAndGet();
            return delegate.callStructured(request);
        }
    }

    /** Fails the schema a fixed number of times, then answers. */
    private static final class FlakyProvider implements LlmProvider {
        private final String id;
        private final int failures;
        private final AtomicInteger calls = new AtomicInteger();

        FlakyProvider(String id, int failures) {
            this.id = id;
            this.failures = failures;
        }

        int calls() {
            return calls.get();
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public ModelTier tier() {
            return ModelTier.CHEAP;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> LlmOutcome<T> callStructured(StructuredRequest<T> request) {
            if (calls.incrementAndGet() <= failures) {
                return LlmOutcome.failed(
                        new LlmFailure(LlmFailure.Kind.SCHEMA_MISMATCH, id, "wandered"));
            }
            return (LlmOutcome<T>) LlmOutcome.answered(
                    new LlmResponse<>("answer", id, "some-model", 10, 5, 0, 12));
        }
    }
}
