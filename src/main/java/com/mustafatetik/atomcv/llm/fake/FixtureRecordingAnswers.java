package com.mustafatetik.atomcv.llm.fake;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.gateway.AnswerRecorder;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The writing half of {@link FixtureStore} (Bolum 54.2).
 *
 * <p>It was missing. {@code FixtureStore.save} was written with a javadoc
 * saying {@code local-record} used it and nothing ever called it, so
 * {@code make record} spent real money on real calls and kept none of the
 * answers — which is why {@code local-fake} has no fixtures for
 * {@code bullet_rewrite}, {@code about_synthesis} or {@code job_analysis},
 * and why four {@code latexTest} cases fail on a synthetic answer.
 *
 * <p>Re-serialises through Jackson rather than keeping the provider's raw
 * body. What replays has to be what {@code FakeLlmProvider} can read back
 * with {@code treeToValue(answer, resultType)}, and the parsed value is the
 * only form known to satisfy that: a raw body carrying a field the record
 * ignores would record clean and replay as a schema mismatch.
 */
@Component
@Profile("local-record")
public class FixtureRecordingAnswers implements AnswerRecorder {

    private static final Logger log = LoggerFactory.getLogger(FixtureRecordingAnswers.class);

    private final FixtureStore fixtures;
    private final ObjectMapper json;

    public FixtureRecordingAnswers(FakeLlmProperties properties, ObjectMapper json) {
        this.fixtures = new FixtureStore(properties.fixtureDir(), json);
        this.json = json;
    }

    @Override
    public void record(StructuredRequest<?> request, Object answer) {
        try {
            var file = fixtures.save(request, json.valueToTree(answer));
            // The path and the prompt reference, which are shape. The answer
            // is derived from the user's content and does not go in a log line
            // (absolute rule 4) — it goes in the file this names.
            log.info("Recorded {} to {}", request.promptRef(), file);
        } catch (RuntimeException e) {
            // A recording run that cannot write is worth finishing: the calls
            // have already been paid for, and the generation under way is
            // still a valid answer to the person who asked for it.
            log.warn("Could not record {}: {}", request.promptRef(), e.toString());
        }
    }

    @Override
    public void discard(StructuredRequest<?> request) {
        try {
            if (fixtures.remove(request)) {
                log.info("Withdrew the recording of {}: the pipeline refused it",
                        request.promptRef());
            }
        } catch (RuntimeException e) {
            log.warn("Could not withdraw {}: {}", request.promptRef(), e.toString());
        }
    }
}
