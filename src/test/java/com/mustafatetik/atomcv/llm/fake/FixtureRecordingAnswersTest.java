package com.mustafatetik.atomcv.llm.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.gateway.JsonSchema;
import com.mustafatetik.atomcv.llm.gateway.LlmOutcome;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bolum 54.2's round trip: what a recording run writes is what a
 * {@code local-fake} run reads back.
 *
 * <p>Writing and reading are asserted together on purpose. Until this class
 * existed the only caller of {@code FixtureStore.save} was a test helper in
 * {@link FakeLlmProviderTest} — so the store looked covered while nothing in
 * the application ever wrote a fixture, and {@code make record} paid for real
 * answers and kept none of them.
 */
class FixtureRecordingAnswersTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The shape a prompt asks for; a record, as the value objects are. */
    record Analysis(String title, int seniority) {
    }

    @TempDir
    Path fixtures;

    @Test
    void whatIsRecordedIsWhatTheFakeProviderReplays() {
        var properties = new FakeLlmProperties(fixtures, false);

        new FixtureRecordingAnswers(properties, JSON)
                .record(request(), new Analysis("Backend Engineer", 4));

        // synthesize=false, so a miss would be a failure rather than a
        // placeholder that happened to parse.
        assertThat(answered(new FakeLlmProvider(properties, JSON).callStructured(request())))
                .isEqualTo(new Analysis("Backend Engineer", 4));
    }

    /**
     * The store overwrites, so a second recording run over the same posting
     * leaves one file rather than a directory that grows every time.
     */
    @Test
    void recordingTheSameCallTwiceLeavesOneFixture() {
        var properties = new FakeLlmProperties(fixtures, false);
        var recorder = new FixtureRecordingAnswers(properties, JSON);

        recorder.record(request(), new Analysis("Backend Engineer", 4));
        recorder.record(request(), new Analysis("Backend Engineer", 5));

        assertThat(answered(new FakeLlmProvider(properties, JSON).callStructured(request())))
                .isEqualTo(new Analysis("Backend Engineer", 5));
    }

    /**
     * A recording run that cannot write has already paid for the call, and the
     * generation under way is still a valid answer to the person waiting for
     * it. The failure is a warning, not an exception thrown through the chain.
     */
    @Test
    void anUnwritableFixtureDirectoryDoesNotFailTheCallThatEarnedIt() {
        var unwritable = new FakeLlmProperties(fixtures.resolve("a-file"), false);
        write(fixtures.resolve("a-file"));

        new FixtureRecordingAnswers(unwritable, JSON)
                .record(request(), new Analysis("Backend Engineer", 4));
    }

    /**
     * The withdrawal Bolum 18.4's gate makes when it refuses what was just
     * recorded. What must be true afterwards is that a replay misses — with
     * {@code synthesize=false} that is a failure, which is how a recording run
     * notices the prompt still needs one.
     */
    @Test
    void aWithdrawnRecordingIsGoneAndReplaysAsAMiss() {
        var properties = new FakeLlmProperties(fixtures, false);
        var recorder = new FixtureRecordingAnswers(properties, JSON);
        recorder.record(request(), new Analysis("Backend Engineer", 4));

        recorder.discard(request());

        assertThat(new FakeLlmProvider(properties, JSON).callStructured(request()))
                .isInstanceOf(LlmOutcome.Failed.class);
    }

    /** Nothing to withdraw is not an error: the chain may never have recorded. */
    @Test
    void withdrawingSomethingNeverRecordedDoesNothing() {
        var properties = new FakeLlmProperties(fixtures, false);

        new FixtureRecordingAnswers(properties, JSON).discard(request());
    }

    private static Analysis answered(LlmOutcome<Analysis> outcome) {
        return ((LlmOutcome.Answered<Analysis>) outcome).response().data();
    }

    private static void write(Path file) {
        try {
            java.nio.file.Files.writeString(file, "not a directory");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static StructuredRequest<Analysis> request() {
        return new StructuredRequest<>("job_analysis", "v1", "system", "a posting",
                new JsonSchema("job_analysis",
                        JSON.createObjectNode().put("type", "object")),
                Analysis.class, ModelTier.CHEAP, Duration.ofSeconds(30));
    }
}
