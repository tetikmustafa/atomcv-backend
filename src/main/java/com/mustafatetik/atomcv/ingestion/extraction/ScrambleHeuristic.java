package com.mustafatetik.atomcv.ingestion.extraction;

import java.util.List;

/**
 * Bolum 31.3's test for text that came out in the wrong order.
 *
 * <p>The section gives it as {@code avgLineLength < 20 || orphanWordRatio >
 * 0.3} and defines neither term. An orphan is a typesetter's word for a line
 * holding a single word, and that is the reading taken here — it is also the
 * one that describes the failure: a two-column PDF read column-blind produces
 * a stack of fragments, many of them one word long.
 *
 * <p><strong>It answers about the shape of the text and never about its
 * quality.</strong> A true answer becomes a sentence in the structuring prompt
 * (Bolum 31.3), not a refusal — a model told the order may be wrong can still
 * read the CV, and a wall here would reject documents that work.
 */
final class ScrambleHeuristic {

    /** Bolum 31.3, verbatim. */
    private static final int MIN_AVERAGE_LINE_LENGTH = 20;

    /** Bolum 31.3, verbatim. */
    private static final double MAX_ORPHAN_RATIO = 0.3;

    private ScrambleHeuristic() {
    }

    static boolean looksScrambled(String text) {
        List<String> lines = text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
        if (lines.isEmpty()) {
            // Nothing to be out of order. The empty case is Bolum 31.2's last
            // rung to answer, not this one's.
            return false;
        }
        double averageLength = lines.stream().mapToInt(String::length).average().orElse(0);
        long orphans = lines.stream().filter(ScrambleHeuristic::isOrphan).count();
        return averageLength < MIN_AVERAGE_LINE_LENGTH
                || (double) orphans / lines.size() > MAX_ORPHAN_RATIO;
    }

    /**
     * A line holding one word.
     *
     * <p>Split on whitespace rather than counted, so "Ada Lovelace" is two and
     * a padded "  Ada  " is one. A heading is an orphan by this test and that
     * is correct — a document that is mostly headings is a document whose body
     * did not survive extraction.
     */
    private static boolean isOrphan(String line) {
        return line.split("\\s+").length == 1;
    }
}
