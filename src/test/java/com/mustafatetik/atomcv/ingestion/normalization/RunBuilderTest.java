package com.mustafatetik.atomcv.ingestion.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bolum 31.5's run generation, and the invariant everything downstream leans
 * on: the runs concatenate back to the sentence.
 *
 * <p>Bolum 12 chose runs over offsets and markup precisely so that no
 * escaping, no drift and no re-matching is ever needed again — but that only
 * holds if the cut is a partition. A builder that dropped a character, or
 * doubled one, would send a subtly wrong sentence to the renderer and to the
 * embedding, and nothing further down could tell.
 */
class RunBuilderTest {

    private static final String SENTENCE =
            "Engineered ETL pipelines processing 300K rows using Microsoft Fabric";

    @Test
    void theRunsAlwaysConcatenateBackToTheSentence() {
        var content = RunBuilder.build(SENTENCE,
                List.of("Microsoft Fabric", "300K rows", "ETL pipelines"),
                List.of("etl", "microsoft-fabric"), List.of("300K rows"));

        assertThat(content.plainText()).isEqualTo(SENTENCE);
    }

    /**
     * The model lists what it thought was important, not what comes first. A
     * forward-only walk from a cursor would silently drop every emphasis
     * listed out of order — here, two of the three.
     */
    @Test
    void emphasisListedOutOfOrderIsStillFound() {
        var content = RunBuilder.build(SENTENCE,
                List.of("Microsoft Fabric", "ETL pipelines"), List.of(), List.of());

        assertThat(markedTexts(content)).containsExactly("ETL pipelines", "Microsoft Fabric");
    }

    /** Bolum 31.5's rule, in the case that names it. */
    @Test
    void anEmphasisTakesItsFirstOccurrence() {
        var content = RunBuilder.build("rows and rows and rows", List.of("rows"),
                List.of(), List.of());

        assertThat(content.runs()).hasSize(2);
        assertThat(content.runs().get(0).text()).isEqualTo("rows");
        assertThat(content.runs().get(0).hasMark(Mark.EMPHASIS)).isTrue();
        assertThat(content.runs().get(1).text()).isEqualTo(" and rows and rows");
    }

    /**
     * Two marks over the same characters would need nested runs, which Bolum
     * 12 does not have. The earlier start wins, and the sentence still
     * concatenates.
     */
    @Test
    void twoOverlappingEmphasesResolveToTheOneThatStartsEarlier() {
        var content = RunBuilder.build(SENTENCE,
                List.of("ETL pipelines processing", "pipelines processing 300K"),
                List.of(), List.of());

        assertThat(markedTexts(content)).containsExactly("ETL pipelines processing");
        assertThat(content.plainText()).isEqualTo(SENTENCE);
    }

    /**
     * The prompt asks for exact quotations for this reason. A fuzzy match
     * would be the code deciding which words the model meant, and a paraphrase
     * marked as a quotation is worse than no bold at all.
     */
    @Test
    void anEmphasisThatIsNotInTheSentenceIsDroppedRatherThanApproximated() {
        var content = RunBuilder.build(SENTENCE,
                List.of("ETL workflows", "Microsoft Fabric"), List.of(), List.of());

        assertThat(markedTexts(content)).containsExactly("Microsoft Fabric");
        assertThat(content.plainText()).isEqualTo(SENTENCE);
    }

    @Test
    void aSentenceWithNoEmphasisIsOnePlainRun() {
        var content = RunBuilder.build(SENTENCE, List.of(), List.of(), List.of());

        assertThat(content.runs()).singleElement()
                .satisfies(run -> assertThat(run.marks()).isEmpty());
        assertThat(content.plainText()).isEqualTo(SENTENCE);
    }

    @Test
    void anEmptySentenceIsEmptyContent() {
        assertThat(RunBuilder.build("", List.of("x"), List.of(), List.of()).isEmpty()).isTrue();
        assertThat(RunBuilder.build(null, List.of(), List.of(), List.of()).isEmpty()).isTrue();
    }

    // -- which mark (Bolum 12: semantic, never presentational) --------------

    @Test
    void aSpanThatIsOneOfTheMetricsIsMarkedAsOne() {
        var content = RunBuilder.build(SENTENCE, List.of("300K rows"),
                List.of("etl"), List.of("300K rows"));

        assertThat(content.runs().stream().filter(run -> run.hasMark(Mark.METRIC)))
                .singleElement()
                .satisfies(run -> assertThat(run.text()).isEqualTo("300K rows"));
    }

    /** Matched through the alias dictionary, so "Microsoft Fabric" finds "microsoft-fabric". */
    @Test
    void aSpanThatCanonicalisesToOneOfTheSkillsIsMarkedAsTechnology() {
        var content = RunBuilder.build(SENTENCE, List.of("Microsoft Fabric"),
                List.of("microsoft-fabric"), List.of());

        assertThat(content.runs().stream().filter(run -> run.hasMark(Mark.TECHNOLOGY)))
                .singleElement()
                .satisfies(run -> assertThat(run.text()).isEqualTo("Microsoft Fabric"));
    }

    /**
     * Everything else is emphasis, including a proper noun. Bolum 31.4 collects
     * products, employers and places into one list, so calling any of them an
     * organisation would be a claim the data does not support — and an unknown
     * mark renders as plain text, losing the emphasis entirely.
     */
    @Test
    void anythingElseIsPlainEmphasis() {
        var content = RunBuilder.build(SENTENCE, List.of("processing"), List.of(), List.of());

        assertThat(content.runs().stream().filter(run -> run.hasMark(Mark.EMPHASIS)))
                .singleElement()
                .satisfies(run -> assertThat(run.text()).isEqualTo("processing"));
    }

    private static List<String> markedTexts(RichContent content) {
        return content.runs().stream()
                .filter(run -> !run.marks().isEmpty())
                .map(Run::text)
                .toList();
    }
}
