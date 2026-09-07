package com.mustafatetik.atomcv.ingestion.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * A {@code .tex} upload, read as the prose inside it (Bolum 31.3).
 *
 * <p>The reference CV this product's template comes from is written with
 * four-argument commands, and reading it back is where the extractor's own
 * shape shows.
 */
class TexTextExtractorTest {

    private final TexTextExtractor extractor = new TexTextExtractor();

    /**
     * <strong>Two arguments are two fields, not one word.</strong>
     *
     * <p>The unwrapping keeps one argument and leaves the rest standing as
     * brace groups; stripping the braces then welds them together. A real
     * upload of a real master CV produced <em>Computer Engineering | GPA:
     * 3.212022 -- 2026</em> — a grade point average and a date range fused into
     * one number — and the model dutifully carried it into an atom, where it
     * became the one line on a produced CV that says something the source
     * document does not.
     */
    @Test
    void twoArgumentsOfOneCommandDoNotRunTogether() {
        String text = extract("""
                \\begin{document}
                \\resumeSubheading
                  {Marmara University}{Istanbul, Turkiye}
                  {Computer Engineering | GPA: 3.21}{2022 -- 2026}
                \\end{document}
                """);

        assertThat(text)
                .contains("Marmara University")
                .contains("Istanbul, Turkiye")
                .contains("Computer Engineering | GPA: 3.21")
                .contains("2022 -- 2026")
                .as("a grade and a date range are not one number")
                .doesNotContain("3.212022");
    }

    /**
     * And a labelled row still reads as one line.
     *
     * <p>{@code \textbf{Label}{: items}} is how the reference template writes a
     * skills matrix, and there the second group <em>continues</em> the first. A
     * separator between every pair of arguments would put a space before the
     * colon on every row of every Tech Stack.
     */
    @Test
    void alabelAndTheListItIntroducesStayOnOneLine() {
        String text = extract("""
                \\begin{document}
                \\textbf{Programming Languages}{: Java, Python, SQL} \\\\
                \\textbf{Turkish}{: Native}
                \\end{document}
                """);

        assertThat(text)
                .contains("Programming Languages: Java, Python, SQL")
                .contains("Turkish: Native");
    }

    /** The preamble is configuration, and configuration is not a CV. */
    @Test
    void thepreambleIsDroppedWhole() {
        String text = extract("""
                \\documentclass[letterpaper,11pt]{article}
                \\usepackage{fontspec}
                \\newcommand{\\resumeItem}[1]{\\item{#1}}
                \\begin{document}
                \\resumeItem{Built ETL pipelines processing 300K+ rows}
                \\end{document}
                """);

        assertThat(text)
                .contains("Built ETL pipelines processing 300K+ rows")
                .doesNotContain("fontspec")
                .doesNotContain("letterpaper");
    }

    /** An escaped character is the character a reader sees. */
    @Test
    void escapesComeBackAsTheirCharacters() {
        String text = extract("""
                \\begin{document}
                \\resumeItem{97.27\\% accuracy on C\\# and R\\&D work}
                \\end{document}
                """);

        assertThat(text).contains("97.27% accuracy on C# and R&D work");
    }

    private String extract(String source) {
        return extractor.extract(source.getBytes(StandardCharsets.UTF_8));
    }
}
