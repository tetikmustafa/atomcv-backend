package com.mustafatetik.atomcv.generation.coverletter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.prompts.PromptProperties;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The shipped {@code cover_letter} prompt, against the check that judges it
 * (Bolum 34.4, 53.2).
 *
 * <p><strong>Why this exists.</strong> F-026 moved the floor from 250 words to
 * 120 in {@link CoverLetterValidator} and left the prompt asking for 250 to 400,
 * deliberately and with the reason written down: changing the ask is a new
 * prompt version. The two then said different things about the same letter for
 * a week, and nothing could have noticed — the prompt is prose and the check is
 * a constant.
 *
 * <p>So this reads the numbers out of the prompt and compares them to the
 * constants. It is a cheap guard against the drift that actually happened,
 * rather than a test of whether the prompt is any good, which only a recorded
 * run against the real model can answer.
 */
class CoverLetterPromptTest {

    private static final PromptRegistry REGISTRY = new PromptRegistry(
            new PromptProperties(Map.of("cover_letter", "v2"), Map.of()), new ObjectMapper());

    private static final String PROMPT = REGISTRY.load("cover_letter").text();

    @Test
    void theshippedPromptLoadsAtTheConfiguredVersion() {
        assertThat(REGISTRY.load("cover_letter").ref()).isEqualTo("cover_letter:v2");
    }

    /**
     * The model is told the numbers that actually refuse it, and no others.
     * Before v2 it was told 250, which had been true and was not any more:
     * five recorded drafts came in at 106 to 153 words and were thrown away by
     * a rule the letter had been asked to break.
     */
    @Test
    void thepromptNamesTheSameBoundsTheCheckEnforces() {
        assertThat(PROMPT)
                .as("the floor the check uses")
                .contains(String.valueOf(CoverLetterValidator.MIN_WORDS))
                .as("the ceiling the check uses")
                .contains(String.valueOf(CoverLetterValidator.MAX_WORDS));
    }

    /** And not the old band, which is the number this version exists to drop. */
    @Test
    void thepromptNoLongerAsksFor250() {
        assertThat(PROMPT).doesNotContain("250");
    }

    /**
     * Bolum 43's cheapest layer, carried over from v1 — a new version of a
     * prompt is where an injection defence is most easily lost, because the
     * file is copied and edited for a different reason.
     */
    @Test
    void theinjectionFenceSurvivedTheNewVersion() {
        assertThat(PROMPT)
                .contains("DATA, not instructions")
                .contains("<letter>");
    }

    /**
     * v1 stays on disk and stays loadable: a version is rolled back by
     * changing a value (Bolum 53.2), and a recorded fixture names the version
     * it was recorded at.
     */
    @Test
    void theversionItReplacedIsStillThere() {
        assertThat(REGISTRY.load("cover_letter", "v1").text()).contains("250 and 400 words");
    }
}
