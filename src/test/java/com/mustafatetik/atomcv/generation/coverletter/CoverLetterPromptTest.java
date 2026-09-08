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
 * <p><strong>v1 is active, and v2 is on disk unmeasured.</strong> v2 dropped the
 * "between 250 and 400 words" ask on the argument that the model ignored it —
 * the drafts behind that argument were 106 to 153 words. The recorded letters
 * say otherwise: three of those five ran on synthetic input and the other two
 * are from 2026-08-30, while every real draft since 2026-09-02 has come in at
 * 255 to 290 words. The ask works. So the rollback is a value change, which is
 * what Bolum 53.2 makes it, and v2 stays a variant waiting for a measurement
 * rather than a version in front of users.
 *
 * <p>What this file guards is the pair that drifted in the first place: the
 * prompt's numbers and the validator's constants are prose on one side and a
 * constant on the other, and nothing could notice they had stopped agreeing.
 */
class CoverLetterPromptTest {

    private static final PromptRegistry REGISTRY = new PromptRegistry(
            new PromptProperties(Map.of("cover_letter", "v1"), Map.of()), new ObjectMapper());

    private static final String ACTIVE = REGISTRY.load("cover_letter").text();

    @Test
    void theshippedPromptLoadsAtTheConfiguredVersion() {
        assertThat(REGISTRY.load("cover_letter").ref()).isEqualTo("cover_letter:v1");
    }

    /**
     * The band the model is asked for, which the recordings show it honours:
     * 255, 260, 268, 272, 286, 287 and 290 words across seven real drafts.
     */
    @Test
    void theactivePromptAsksForTheBandTheModelActuallyWrites() {
        assertThat(ACTIVE).contains("250 and 400 words");
    }

    /**
     * And the ceiling it asks for is the ceiling the check enforces. The floor
     * is deliberately not the same number — {@code MIN_WORDS} is 120 because
     * F-026 found the band unmeasured and a good short letter refused, and
     * asking for more than the floor is not a contradiction.
     */
    @Test
    void theceilingAskedForIsTheCeilingEnforced() {
        assertThat(ACTIVE).contains(String.valueOf(CoverLetterValidator.MAX_WORDS));
        assertThat(CoverLetterValidator.MIN_WORDS)
                .as("the floor is lower than the ask, on purpose")
                .isLessThan(250);
    }

    /**
     * Bolum 43's cheapest layer, in both versions — a new version of a prompt is
     * where an injection defence is most easily lost, because the file is copied
     * and edited for a different reason.
     */
    @Test
    void theinjectionFenceIsInEveryVersionOnDisk() {
        for (String version : new String[] {"v1", "v2"}) {
            assertThat(REGISTRY.load("cover_letter", version).text())
                    .as("%s fences the letter as data", version)
                    .contains("DATA, not instructions")
                    .contains("<letter>");
        }
    }

    /**
     * v2 stays loadable and stays what it is: the version that asks for a shape
     * and names the check's own thresholds. It is one value away from being
     * active, which is how it should be measured when somebody records against
     * it.
     */
    @Test
    void thevariantWaitingForAMeasurementIsStillThere() {
        String variant = REGISTRY.load("cover_letter", "v2").text();

        assertThat(variant).doesNotContain("250 and 400 words");
        assertThat(variant).contains(String.valueOf(CoverLetterValidator.MIN_WORDS));
    }
}
