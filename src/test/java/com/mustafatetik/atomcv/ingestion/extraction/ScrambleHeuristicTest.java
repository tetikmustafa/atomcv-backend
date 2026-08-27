package com.mustafatetik.atomcv.ingestion.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Bolum 31.3's heuristic, on the two shapes it has to tell apart.
 *
 * <p>The cases that matter are the ones near the line: a real CV must not be
 * flagged, because the flag becomes a sentence in the prompt telling a model
 * the text may be out of order — and a model told that about text which is
 * fine may set about "fixing" what was already right.
 */
class ScrambleHeuristicTest {

    /** What a two-column PDF read column-blind actually looks like. */
    private static final String FRAGMENTS = """
            Ada
            Lovelace
            2019
            London
            Analytical
            Engine
            Mathematician
            Notes
            """;

    private static final String ORDINARY_CV = """
            Ada Lovelace — Mathematician and the first computer programmer
            Engineered analytical methods for the Analytical Engine, 1843
            Published the first algorithm intended for machine execution
            Translated and annotated Menabrea's memoir, tripling its length
            """;

    @Test
    void aStackOfOneWordFragmentsLooksScrambled() {
        assertThat(ScrambleHeuristic.looksScrambled(FRAGMENTS)).isTrue();
    }

    @Test
    void anOrdinaryCvDoesNot() {
        assertThat(ScrambleHeuristic.looksScrambled(ORDINARY_CV)).isFalse();
    }

    /**
     * A few short lines among long ones are headings, not damage.
     *
     * <p>Bolum 31.3 puts the orphan threshold at three in ten, and this sits
     * just under it: two headings in eight lines. A stricter reading would
     * flag every CV that has section titles, which is every CV.
     */
    @Test
    void headingsAmongProseStayUnderTheThreshold() {
        String withHeadings = """
                Experience
                Engineered ETL pipelines processing 300 thousand rows nightly
                Cut the nightly batch from four hours down to forty minutes
                Led the migration of three services onto the new platform
                Education
                Studied mathematics at the University of London, 1833 to 1836
                Wrote the first published algorithm for machine execution
                Translated and annotated Menabrea's memoir on the engine
                """;

        assertThat(ScrambleHeuristic.looksScrambled(withHeadings)).isFalse();
    }

    /**
     * Long lines cannot rescue a document that is mostly orphans.
     *
     * <p>The two halves of the rule are an {@code or} and each has to be able
     * to fire alone; averaging them into one number would let a single
     * paragraph hide a column of wreckage.
     */
    @Test
    void theOrphanHalfFiresEvenWhenTheAverageLineIsLong() {
        String mixed = """
            Ada
            Lovelace
            Engine
            Mathematician who wrote the first published algorithm for a machine
            Translated and annotated Menabrea's memoir, tripling it in length
            """;

        assertThat(ScrambleHeuristic.looksScrambled(mixed)).isTrue();
    }

    /**
     * And the average half fires when nothing is an orphan.
     *
     * <p>Every line here has two words, so the orphan ratio is zero; what is
     * wrong with the text is that its lines are eight characters long.
     */
    @Test
    void theAverageHalfFiresWhenNothingIsAnOrphan() {
        String short_ = "Ada L\nAda L\nAda L\nAda L\nAda L\nAda L\n";

        assertThat(ScrambleHeuristic.looksScrambled(short_)).isTrue();
    }

    /** Blank lines are not evidence of anything and must not be counted. */
    @Test
    void blankLinesAreNotOrphans() {
        String spaced = ORDINARY_CV.replace("\n", "\n\n");

        assertThat(ScrambleHeuristic.looksScrambled(spaced)).isFalse();
    }

    /**
     * Emptiness is Bolum 31.2's last rung to answer, not this one's.
     *
     * <p>Answering true here would put "the text may be out of order" in front
     * of a model that is about to be handed nothing at all.
     */
    @Test
    void nothingAtAllIsNotScrambled() {
        assertThat(ScrambleHeuristic.looksScrambled("")).isFalse();
        assertThat(ScrambleHeuristic.looksScrambled("   \n\n  ")).isFalse();
    }
}
