package com.mustafatetik.atomcv.ingestion.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile;
import com.mustafatetik.atomcv.shared.wire.ExtractionWarningCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P3 on the ingestion side, against the two inventions that reached a real CV.
 *
 * <p>Both are quoted from the document that produced them, so this cannot pass
 * by agreeing with a paraphrase of itself: the source lines are the ones in the
 * uploaded CV, and the atom texts are the ones the extraction wrote into the
 * database.
 */
class ExtractionFidelityTest {

    /**
     * The real bullet, verbatim. It says SQL. It does not say SQL Server, and
     * the difference is a product the person has never used.
     */
    private static final String SOURCE_BULLET =
            "Integrated structured enterprise data utilizing SQL queries to model "
                    + "complex business reporting logic.";

    /** What the extraction wrote instead. */
    private static final String EXTRACTED_BULLET =
            "Integrated structured enterprise data utilizing advanced SQL Server "
                    + "queries, optimizing analytic data layers.";

    @Test
    void atechnologyTheDocumentDoesNotNameIsReported() {
        var warning = ExtractionFidelity.check(entryOf(EXTRACTED_BULLET), SOURCE_BULLET);

        assertThat(warning).isPresent();
        assertThat(warning.get().code())
                .isEqualTo(ExtractionWarningCode.UNSUPPORTED_BY_SOURCE);
        assertThat(warning.get().detail()).contains("Server");
    }

    /** The Kafka half: invented outright rather than sharpened. */
    @Test
    void awholeInventionIsReported() {
        String source = "An agile fast learner eager to explore modern caching (Redis).";
        String extracted =
                "An agile fast learner eager to explore modern caching and message "
                        + "queues (Redis, Kafka).";

        var warning = ExtractionFidelity.check(entryOf(extracted), source);

        assertThat(warning).isPresent();
        assertThat(warning.get().detail()).contains("Kafka").doesNotContain("Redis");
    }

    /** A faithful extraction says nothing, which is most of them. */
    @Test
    void anextractionThatStayedOnTheDocumentIsSilent() {
        assertThat(ExtractionFidelity.check(entryOf(SOURCE_BULLET), SOURCE_BULLET)).isEmpty();
    }

    /**
     * Rewording is extraction's job — bullets are trimmed and tidied. Only
     * names are held to the document.
     */
    @Test
    void rewordingWithoutInventingANameIsAllowed() {
        var warning = ExtractionFidelity.check(
                entryOf("Modelled reporting logic with SQL over enterprise data."),
                SOURCE_BULLET);

        assertThat(warning).isEmpty();
    }

    /** An entry typed into the editor has no document, and invents nothing. */
    @Test
    void withoutADocumentThereIsNothingToCheckAgainst() {
        assertThat(ExtractionFidelity.check(entryOf(EXTRACTED_BULLET), null)).isEmpty();
        assertThat(ExtractionFidelity.check(entryOf(EXTRACTED_BULLET), "  ")).isEmpty();
    }

    private static ExtractedProfile.ExtractedEntry entryOf(String text) {
        var atom = new ExtractedProfile.ExtractedAtom(text, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
        return new ExtractedProfile.ExtractedEntry(
                "Part-time Data Engineer", "Brisa", "Istanbul", "2025-09", null, List.of(atom));
    }
}
