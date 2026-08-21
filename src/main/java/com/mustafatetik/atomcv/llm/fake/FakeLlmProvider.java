package com.mustafatetik.atomcv.llm.fake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.gateway.LlmFailure;
import com.mustafatetik.atomcv.llm.gateway.LlmOutcome;
import com.mustafatetik.atomcv.llm.gateway.LlmProvider;
import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * A provider that costs nothing (Bolum 54.2).
 *
 * <p>Written before any real adapter on purpose: with it in place the rest of
 * Stage 2 — the phases, the queue, the error paths, the frontend's screens —
 * is built without spending a cent, and a recorded fixture makes the answer
 * the same one every run.
 *
 * <p>Two sources, in order. A fixture recorded by {@code local-record} is
 * replayed as-is; a call no fixture covers falls back to a schema-shaped
 * placeholder, or fails if {@code synthesize} is off. The second half is what
 * makes the provider usable on a fresh clone with an empty fixture directory.
 */
@Component
@Profile("local-fake")
public class FakeLlmProvider implements LlmProvider {

    public static final String ID = "fake";

    private final FakeLlmProperties properties;
    private final FixtureStore fixtures;
    private final ObjectMapper json;

    public FakeLlmProvider(FakeLlmProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.fixtures = new FixtureStore(properties.fixtureDir(), json);
    }

    @Override
    public String id() {
        return ID;
    }

    /** Always: it is the only provider this profile has, and it needs no key. */
    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Answers for both chains. A fake that belonged to one tier would leave
     * the other unable to run at all under {@code local-fake}.
     */
    @Override
    public ModelTier tier() {
        return ModelTier.CHEAP;
    }

    @Override
    public <T> LlmOutcome<T> callStructured(StructuredRequest<T> request) {
        long startedAt = System.nanoTime();
        var recorded = fixtures.find(request);
        if (recorded.isEmpty() && !properties.synthesize()) {
            return LlmOutcome.failed(new LlmFailure(LlmFailure.Kind.REQUEST_REJECTED, ID,
                    "no fixture for " + request.promptRef() + " and synthesize is off"));
        }
        var answer = recorded.orElseGet(() ->
                SyntheticAnswer.fromSchema(request.outputSchema(), seedOf(request)));

        return parse(request, answer, startedAt);
    }

    private <T> LlmOutcome<T> parse(
            StructuredRequest<T> request, JsonNode answer, long startedAt) {
        try {
            var value = json.treeToValue(answer, request.resultType());
            return LlmOutcome.answered(new LlmResponse<>(value, ID, "fake-model",
                    // Token counts a real provider would bill. Zero rather than
                    // a made-up figure: a cost report from local-fake should
                    // read as zero, not as a number someone might trust.
                    0, 0, 0, (System.nanoTime() - startedAt) / 1_000_000));
        } catch (Exception e) {
            // The same failure a real provider produces when the model answers
            // in a shape the schema does not allow — which is how a fixture
            // that has gone stale against a changed result type surfaces.
            return LlmOutcome.failed(new LlmFailure(LlmFailure.Kind.SCHEMA_MISMATCH, ID,
                    "fixture does not fit " + request.resultType().getSimpleName()));
        }
    }

    /**
     * Same request, same placeholder. Derived from the prompt reference and
     * the user prompt's length rather than its text, so that nothing about the
     * content decides the answer.
     */
    private static long seedOf(StructuredRequest<?> request) {
        return (long) request.promptRef().hashCode() * 31 + request.userPrompt().length();
    }
}
