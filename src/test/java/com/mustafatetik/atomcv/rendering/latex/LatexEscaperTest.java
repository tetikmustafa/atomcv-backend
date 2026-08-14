package com.mustafatetik.atomcv.rendering.latex;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import org.junit.jupiter.api.Test;

/**
 * Escaping used to be a rule in a prompt (Bolum 22.3). These tests are why it
 * is code: every one of them is a document that would otherwise fail to
 * compile, or compile into something the user did not write.
 */
class LatexEscaperTest {

    @Test
    void theTenCharactersThatMeanSomethingToTexComeOutMeaningThemselves() {
        assertThat(LatexEscaper.escape("50% & C# in {braces}"))
                .isEqualTo("50\\% \\& C\\# in \\{braces\\}");
        assertThat(LatexEscaper.escape("cost_per_unit ~ $5 ^ 2"))
                .isEqualTo("cost\\_per\\_unit \\textasciitilde{} \\$5 \\textasciicircum{} 2");
        assertThat(LatexEscaper.escape("a\\b")).isEqualTo("a\\textbackslash{}b");
    }

    @Test
    void turkishTextPassesThroughUntouched() {
        // XeLaTeX takes UTF-8 directly; escaping these would break them.
        assertThat(LatexEscaper.escape("İstanbul'da çalıştım — ölçüm"))
                .isEqualTo("İstanbul'da çalıştım — ölçüm");
    }

    @Test
    void anEmptyOrMissingStringIsEmpty() {
        assertThat(LatexEscaper.escape(null)).isEmpty();
        assertThat(LatexEscaper.escape("")).isEmpty();
    }

    @Test
    void aUrlKeepsWhatItNeedsAndLosesWhatWouldBreakTheDocument() {
        assertThat(LatexEscaper.escapeUrl("https://example.com/a?b=1&c=2#top"))
                .isEqualTo("https://example.com/a?b=1\\&c=2\\#top");
        // A brace or a backslash would end the \href argument early.
        assertThat(LatexEscaper.escapeUrl("https://example.com/{evil}\\x"))
                .isEqualTo("https://example.com/evilx");
    }

    @Test
    void marksBecomeTheCommandsTheTemplateChose() {
        var content = RichContent.of(
                Run.of("Built "),
                Run.of("ETL", Mark.TECHNOLOGY),
                Run.of(" for "),
                Run.of("300K+ rows", Mark.METRIC),
                Run.of(" — "),
                Run.of("carefully", Mark.EMPHASIS));

        assertThat(LatexInlineRenderer.render(content))
                .isEqualTo("Built \\textbf{ETL} for \\textbf{300K+ rows} — \\textit{carefully}");
    }

    @Test
    void aLinkCarriesItsTargetThroughHref() {
        var content = RichContent.of(Run.link("mustafatetik.com", "https://mustafatetik.com"));

        assertThat(LatexInlineRenderer.render(content))
                .isEqualTo("\\href{https://mustafatetik.com}{mustafatetik.com}");
    }

    /** Bolum 16.2: a mark from a newer build must not stop a document. */
    @Test
    void anUnknownMarkRendersAsPlainText() {
        var content = RichContent.of(Run.of("Go", Mark.TECHNOLOGY, new Mark("sarcasm")));

        assertThat(LatexInlineRenderer.render(content)).isEqualTo("\\textbf{Go}");
    }

    @Test
    void anOrganizationMarkIsSemanticNotVisual() {
        // It exists for scoring; the page treats it as ordinary text.
        var content = RichContent.of(Run.of("Acme", Mark.ORGANIZATION));

        assertThat(LatexInlineRenderer.render(content)).isEqualTo("Acme");
    }

    @Test
    void escapingHappensBeforeMarkupSoUserTextCannotInjectCommands() {
        var content = RichContent.of(Run.of("\\textbf{not mine}", Mark.EMPHASIS));

        assertThat(LatexInlineRenderer.render(content))
                .isEqualTo("\\textit{\\textbackslash{}textbf\\{not mine\\}}");
    }
}
