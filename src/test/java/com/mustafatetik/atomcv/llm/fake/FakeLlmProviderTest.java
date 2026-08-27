package com.mustafatetik.atomcv.llm.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.gateway.JsonSchema;
import com.mustafatetik.atomcv.llm.gateway.LlmFailure;
import com.mustafatetik.atomcv.llm.gateway.LlmOutcome;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Bolum 54.2: the provider the rest of Stage 2 is built against. */
class FakeLlmProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The shape the probe schema describes. */
    record Analysis(String title, String seniority, List<String> skills,
                    double confidence, boolean remote, int headcount) {
    }

    @TempDir
    Path fixtures;

    @Test
    void aCallWithNoFixtureIsAnsweredInTheShapeOfTheSchema() {
        var outcome = provider(true).callStructured(request("a posting"));

        var analysis = answered(outcome);
        assertThat(analysis.title()).startsWith("synthetic-");
        assertThat(analysis.seniority()).isIn("junior", "mid", "senior");
        assertThat(analysis.skills()).isNotEmpty();
    }

    /**
     * The point of a fake over a stub: the same input answers the same way
     * every run, so a failing pipeline test failed for a reason.
     */
    /**
     * A placeholder that violates the schema it was built from is not a
     * placeholder, it is a second definition of the contract.
     *
     * <p>It had a cost worth naming. An unbounded draw put a {@code 0..1}
     * confidence anywhere in {@code 0..99.9}, and the few draws that landed
     * under a gate's floor made {@code make dev} fail for a reason no reader
     * could see — a flake in the one place a developer has no fixture to
     * compare against.
     */
    @Test
    void asyntheticNumberStaysInsideTheBoundsTheSchemaDeclares() {
        for (int i = 0; i < 50; i++) {
            var analysis = answered(provider(true).callStructured(request("posting " + i)));

            assertThat(analysis.confidence()).isBetween(0.0, 1.0);
        }
    }

    @Test
    void thesameRequestAnswersIdenticallyEveryTime() {
        var provider = provider(true);

        assertThat(answered(provider.callStructured(request("a posting"))))
                .isEqualTo(answered(provider.callStructured(request("a posting"))));
    }

    @Test
    void twoDifferentPostingsDoNotGetTheSameAnswer() {
        var provider = provider(true);

        assertThat(answered(provider.callStructured(request("backend role"))))
                .isNotEqualTo(answered(provider.callStructured(request("frontend role"))));
    }

    // ── Fixtures win over placeholders ────────────────────────────────────

    @Test
    void aRecordedFixtureIsReplayedRatherThanSynthesized() {
        var request = request("a posting");
        record(request, """
                {"title":"Senior Backend Engineer","seniority":"senior",
                 "skills":["java","postgres"],"confidence":0.91,
                 "remote":true,"headcount":3}
                """);

        var analysis = answered(provider(true).callStructured(request));

        assertThat(analysis.title()).isEqualTo("Senior Backend Engineer");
        assertThat(analysis.skills()).containsExactly("java", "postgres");
    }

    /**
     * The fixture is found by a hash of the input, so a changed posting must
     * miss. Replaying an unrelated recording would be worse than not having
     * one — the pipeline would run on an answer about a different job.
     */
    @Test
    void aFixtureRecordedForAnotherPostingIsNotReplayed() {
        record(request("a posting"), """
                {"title":"Recorded","seniority":"senior","skills":["java"],
                 "confidence":0.9,"remote":true,"headcount":1}
                """);

        var analysis = answered(provider(true).callStructured(request("a different posting")));

        assertThat(analysis.title()).isNotEqualTo("Recorded");
    }

    @Test
    void aFixtureRecordedForAnotherPromptVersionIsNotReplayed() {
        record(request("a posting"), """
                {"title":"Recorded","seniority":"senior","skills":["java"],
                 "confidence":0.9,"remote":true,"headcount":1}
                """);
        var v2 = new StructuredRequest<>("probe_prompt", "v2", "", "a posting",
                schema(), Analysis.class, ModelTier.CHEAP, Duration.ofSeconds(30));

        assertThat(answered(provider(true).callStructured(v2)).title()).isNotEqualTo("Recorded");
    }

    // ── Recording mode ────────────────────────────────────────────────────

    /**
     * Bolum 54.2: a miss under {@code local-record} has to become a real call,
     * so a placeholder must never be recorded as though a model had said it.
     */
    @Test
    void withSynthesisOffAMissIsAFailureRatherThanAPlaceholder() {
        var outcome = provider(false).callStructured(request("a posting"));

        assertThat(outcome).isInstanceOf(LlmOutcome.Failed.class);
        var failure = ((LlmOutcome.Failed<Analysis>) outcome).failure();
        assertThat(failure.kind()).isEqualTo(LlmFailure.Kind.REQUEST_REJECTED);
        assertThat(failure.kind().tryNextProvider()).isFalse();
    }

    /** A fixture recorded before a field was added must fail loudly. */
    @Test
    void aFixtureThatNoLongerFitsTheResultTypeReportsASchemaMismatch() {
        var request = request("a posting");
        record(request, """
                {"title":"Recorded","seniority":"senior","skills":"not-a-list",
                 "confidence":0.9,"remote":true,"headcount":1}
                """);

        var outcome = provider(true).callStructured(request);

        assertThat(((LlmOutcome.Failed<Analysis>) outcome).failure().kind())
                .isEqualTo(LlmFailure.Kind.SCHEMA_MISMATCH);
    }

    // ── The provider itself ───────────────────────────────────────────────

    @Test
    void theFakeNeedsNoKeyAndReportsNoCost() {
        var provider = provider(true);
        assertThat(provider.isAvailable()).isTrue();
        assertThat(provider.id()).isEqualTo("fake");

        var response = ((LlmOutcome.Answered<Analysis>)
                provider.callStructured(request("a posting"))).response();

        // A made-up token count would show up in a cost report as a number
        // someone might act on.
        assertThat(response.inputTokens()).isZero();
        assertThat(response.outputTokens()).isZero();
        assertThat(response.provider()).isEqualTo("fake");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private FakeLlmProvider provider(boolean synthesize) {
        return new FakeLlmProvider(new FakeLlmProperties(fixtures, synthesize), JSON);
    }

    private void record(StructuredRequest<?> request, String answer) {
        try {
            new FixtureStore(fixtures, JSON).save(request, JSON.readTree(answer));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Analysis answered(LlmOutcome<Analysis> outcome) {
        return ((LlmOutcome.Answered<Analysis>) outcome).response().data();
    }

    private static StructuredRequest<Analysis> request(String posting) {
        return new StructuredRequest<>("probe_prompt", "v1", "system", posting,
                schema(), Analysis.class, ModelTier.CHEAP, Duration.ofSeconds(30));
    }

    private static JsonSchema schema() {
        try (var in = FakeLlmProviderTest.class.getClassLoader()
                .getResourceAsStream("prompts/probe_prompt/schema.json")) {
            return new JsonSchema("probe_prompt", JSON.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
