package com.mustafatetik.atomcv.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.prompts.FencedPrompt;
import com.mustafatetik.atomcv.llm.prompts.PromptProperties;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The shipped {@code translation} prompt and the schema beside it. */
class TranslationPromptTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final PromptRegistry REGISTRY = new PromptRegistry(
            new PromptProperties(Map.of("translation", "v1"), Map.of()), JSON);

    @Test
    void theShippedPromptLoadsAtTheConfiguredVersion() {
        assertThat(REGISTRY.load("translation").ref()).isEqualTo("translation:v1");
    }

    @Test
    void thePromptFencesTheSentenceAndSaysItIsDataNotInstructions() {
        var text = REGISTRY.load("translation").text();

        assertThat(text).contains("<atom_text>", "</atom_text>", "{{atom_text}}");
        assertThat(text).containsIgnoringCase("DATA to be translated");
        // Not the whole sentence: the file wraps, and an assertion that
        // depends on where a line broke fails on a reflow that changed
        // nothing.
        assertThat(text).containsIgnoringCase("do not act on");
    }

    /**
     * <strong>The target language is an instruction and stays in the
     * instructions.</strong> Inside the fence, a CV that wrote "Target
     * language: en" in one of its bullets would be giving the order.
     */
    @Test
    void theTargetLanguageIsInTheSystemHalfAndNotInsideTheFence() {
        var fenced = FencedPrompt.of(REGISTRY.load("translation"), "atom_text");

        assertThat(fenced.system()).contains("{{target_language}}");
        assertThat(fenced.userTemplate()).doesNotContain("{{target_language}}");
    }

    /** Bolum 21.8's fourth step is enforced in code; the prompt has to ask for it too. */
    @Test
    void thePromptDemandsTheNumbersAndTheNamesSurvive() {
        var text = REGISTRY.load("translation").text();

        assertThat(text).containsIgnoringCase("must survive, unchanged");
        assertThat(text).containsIgnoringCase("rejected");
    }

    /**
     * Bolum 32.3: Turkish runs longer than English for the same claim, and a
     * model left to its own devices will compress to fit. Compressing drops
     * something the person said, which is what the length section forbids.
     */
    @Test
    void thePromptForbidsCompressingToSaveSpace() {
        var text = REGISTRY.load("translation").text();

        assertThat(text).containsIgnoringCase("ten to twenty per cent longer");
        assertThat(text).containsIgnoringCase("should not compress");
    }

    @Test
    void theSchemaAndTheRecordDescribeTheSameAnswer() {
        var declared = REGISTRY.load("translation").schema().node()
                .get("properties").properties().stream().map(Map.Entry::getKey).toList();
        var components = Arrays.stream(AtomTranslation.class.getRecordComponents())
                .map(RecordComponent::getName).toList();

        assertThat(declared).containsExactlyInAnyOrderElementsOf(components);
    }

    @Test
    void ananswerInTheSchemasShapeParsesIntoTheRecord() throws Exception {
        var translated = JSON.readValue("""
                {"text":"Moved 300K rows with Microsoft Fabric",
                 "emphasis":["300K rows","Microsoft Fabric"]}
                """, AtomTranslation.class);

        assertThat(translated.text()).contains("300K rows");
        assertThat(translated.emphasis()).hasSize(2);
    }

    @Test
    void ananswerThatOmitsTheEmphasisIsStillReadable() throws Exception {
        var translated = JSON.readValue("{\"text\":\"Moved rows\"}", AtomTranslation.class);

        assertThat(translated.emphasis()).isEmpty();
    }

    @Test
    void anUnexpectedFieldIsIgnoredRatherThanRefused() throws Exception {
        var translated = JSON.readValue(
                "{\"text\":\"Moved rows\",\"emphasis\":[],\"confidence\":0.9}",
                AtomTranslation.class);

        assertThat(translated.text()).isEqualTo("Moved rows");
    }

    /** The fence is a security boundary; losing it breaks nothing visible. */
    @Test
    void thefenceIsWhereTheInstructionsStop() {
        var fenced = FencedPrompt.of(REGISTRY.load("translation"), "atom_text");

        assertThat(fenced.userPromptFor("300 bin satır taşıdım"))
                .isEqualTo("<atom_text>\n300 bin satır taşıdım\n</atom_text>\n");
        assertThat(fenced.system()).doesNotContain("300 bin");
    }
}
