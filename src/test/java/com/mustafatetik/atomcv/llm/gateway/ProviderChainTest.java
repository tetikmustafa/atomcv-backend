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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Bolum 27.3: the order, the skips, and the one failure that stops the walk. */
class ProviderChainTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-21T09:00:00Z"), ZoneOffset.UTC);

    private final List<LlmInvocationEvent> published = new ArrayList<>();

    /** Bolum 48.3's fallback rate is read off this one. */
    private final io.micrometer.core.instrument.simple.SimpleMeterRegistry meters =
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    private final List<Object> recorded = new ArrayList<>();

    /** Absent unless a test asks for it, as it is absent outside local-record. */
    private AnswerRecorder recorder;

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

    // -- Bolum 48.3's "Saglayici fallback orani" ----------------------------

    /**
     * An answer from the first provider that was actually tried is not a
     * fallback, and that is the denominator half of the rate.
     */
    @Test
    void ananswerFromTheFirstProviderCountsAsPrimary() {
        chain(List.of(answering("gemini"), answering("deepseek"))).call(request());

        assertThat(answersAt("primary")).isEqualTo(1);
        assertThat(meters.find("llm.chain.answers").tag("position", "fallback").counter())
                .isNull();
    }

    /** And an answer bought after a failure is the numerator. */
    @Test
    void ananswerAfterAfailureCountsAsAfallback() {
        chain(List.of(
                failing("gemini", LlmFailure.Kind.RATE_LIMITED),
                answering("deepseek"))).call(request());

        assertThat(answersAt("fallback")).isEqualTo(1);
    }

    /**
     * <strong>A provider with no key is skipped without counting.</strong>
     * Bolum 27.3 says so about the {@code tried} list and the rate has to
     * agree: a deployment running with one key out of three would otherwise
     * report every single answer as a fallback, and the number that is
     * supposed to say "a vendor is having a bad day" would instead say
     * "somebody did not buy three subscriptions".
     */
    @Test
    void askippedProviderWithNoKeyDoesNotMakeTheAnswerAfallback() {
        chain(List.of(unavailable("openai"), answering("gemini")),
                List.of("openai", "gemini"), 0).call(request());

        assertThat(answersAt("primary")).isEqualTo(1);
    }

    /** The walk that ran out is counted at its own name, not as an answer. */
    @Test
    void anexhaustedChainIsCountedSeparately() {
        chain(List.of(unavailable("openai"), unavailable("gemini"))).call(request());

        assertThat(meters.get("llm.chain.exhausted").tag("tier", "cheap").counter().count())
                .isEqualTo(1);
        assertThat(meters.find("llm.chain.answers").counters()).isEmpty();
    }

    private double answersAt(String position) {
        return meters.get("llm.chain.answers")
                .tag("tier", "cheap").tag("position", position).counter().count();
    }

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

    /**
     * Bolum 54.2. The recorder was written, documented as used by
     * {@code local-record}, and never called — so {@code make record} paid for
     * real answers and kept none. This is the test that would have said so.
     */
    @Test
    void aRecordingRunKeepsTheAnswerAProviderGave() {
        recorder = keeping();

        chain(List.of(answering("gemini"))).call(request());

        assertThat(recorded).containsExactly("answer");
    }

    /** There is no answer to keep, and a recorded failure would replay as one. */
    @Test
    void nothingIsRecordedWhenTheChainRunsOut() {
        recorder = keeping();

        chain(List.of(failing("gemini", LlmFailure.Kind.RATE_LIMITED))).call(request());

        assertThat(recorded).isEmpty();
    }

    /**
     * The chain only ever records. Withdrawing is Bolum 18.4's gate, which
     * sits a layer above and has its own tests — so this one fails loudly if
     * the chain ever starts doing it.
     */
    private AnswerRecorder keeping() {
        return new AnswerRecorder() {
            @Override
            public void record(StructuredRequest<?> request, Object answer) {
                recorded.add(answer);
            }

            @Override
            public void discard(StructuredRequest<?> request) {
                throw new AssertionError("the chain does not judge an answer");
            }
        };
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
                event -> published.add((LlmInvocationEvent) event), CLOCK,
                Optional.ofNullable(recorder), meters, new ProviderBreakers(meters));
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

    // ── Bolum 5.1's circuit breaker ───────────────────────────────────────

    /**
     * <strong>The failure this buys.</strong> Without a breaker the chain still
     * produced an answer during an outage — it just asked the dead vendor
     * first, every single time, and paid the call timeout before moving on.
     * This asserts the vendor stops being asked, which is the only observable
     * difference and the one a deleted breaker would give back.
     */
    @Test
    void adeadProviderStopsBeingAskedOnceTheWindowSaysSo() {
        var dead = new CountingProvider(failing("gemini", LlmFailure.Kind.UNREACHABLE));
        var alive = answering("openrouter");
        var chain = chain(List.of(dead, alive), List.of("gemini", "openrouter"), 0);

        // minimumNumberOfCalls is five, slidingWindowSize ten: five failures is
        // the earliest the breaker is allowed to decide anything.
        for (int i = 0; i < 5; i++) {
            assertThat(ok(chain.call(request())).provider()).isEqualTo("openrouter");
        }
        int askedWhileClosed = dead.calls();
        assertThat(askedWhileClosed).as("every call paid the dead vendor first").isEqualTo(5);

        for (int i = 0; i < 5; i++) {
            assertThat(ok(chain.call(request())).provider())
                    .as("the product keeps working -- that was never the problem")
                    .isEqualTo("openrouter");
        }
        assertThat(dead.calls())
                .as("and stops paying for the vendor it already knows is dark")
                .isEqualTo(askedWhileClosed);
    }

    /**
     * <strong>A schema mismatch must not open anything.</strong> It is a fact
     * about the prompt, and a breaker that counted it would pull a healthy
     * vendor out of every chain over one bad prompt version.
     */
    @Test
    void aschemaMismatchNeverOpensTheCircuit() {
        var picky = new CountingProvider(failing("gemini", LlmFailure.Kind.SCHEMA_MISMATCH));
        var chain = chain(List.of(picky, answering("openrouter")),
                List.of("gemini", "openrouter"), 0);

        for (int i = 0; i < 10; i++) {
            chain.call(request());
        }

        assertThat(picky.calls())
                .as("asked every time, because it answered every time")
                .isEqualTo(10);
    }

    /**
     * A vendor whose breaker is open is still named in the error. It was
     * configured and it is failing, which is part of why the walk ran out;
     * omitting it hands a user an empty {@code tried} during the outage the
     * field exists to describe.
     */
    @Test
    void anopenProviderIsStillReportedAsTried() {
        var chain = chain(List.of(failing("gemini", LlmFailure.Kind.UNREACHABLE)),
                List.of("gemini"), 0);

        for (int i = 0; i < 5; i++) {
            chain.call(request());
        }

        assertThat(err(chain.call(request())).tried()).containsExactly("gemini");
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
