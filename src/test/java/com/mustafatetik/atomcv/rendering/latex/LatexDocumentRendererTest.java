package com.mustafatetik.atomcv.rendering.latex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import com.mustafatetik.atomcv.rendering.model.MeasurementRequest;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.HexColor;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LatexDocumentRendererTest {

    private final LatexDocumentRenderer renderer = new LatexDocumentRenderer();

    private static final RichContent BULLET = RichContent.of(
            Run.of("Built "),
            Run.of("ETL", Mark.TECHNOLOGY),
            Run.of(" pipelines processing "),
            Run.of("300K+ rows", Mark.METRIC));

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(Locale.ENGLISH);
    }

    /**
     * Adim 1.4's critical test. A measurement taken under a different preamble
     * measures a document nobody will print, and the page guarantee is built
     * on the two being identical.
     */
    @Test
    void measurementAndFinalUseTheSamePreamble() {
        var customization = new TemplateCustomization(
                "classic", FontFamily.BOOK, 11.5, 0.75, 1.15, HexColor.of("2E5AAC"));

        String measurement = renderer.renderMeasurement(
                new MeasurementRequest(List.of(item("var-1", BULLET)), customization)).value();
        String finalDocument = renderer.renderFinal(request(customization)).value();

        assertThat(preambleOf(measurement))
                .isEqualTo(preambleOf(finalDocument))
                .contains("\\setmainfont{TeX Gyre Pagella}")
                .contains("margin=0.75in")
                .contains("\\linespread{1.15}")
                .contains("{2E5AAC}")
                .contains("[letterpaper,12pt]");
    }

    @Test
    void theMeasurementDocumentSetsContentAtTheWidthTheFinalOneUses() {
        String measurement = renderer.renderMeasurement(
                new MeasurementRequest(List.of(item("var-1", BULLET)),
                        TemplateCustomization.CLASSIC)).value();

        assertThat(measurement)
                // Inside the same environment the final document uses, at the
                // width a bullet actually gets — \linewidth inside an itemize
                // is already reduced by the indent.
                .contains("\\begin{itemize}")
                .contains("\\item\\savebox")
                .contains("\\parbox{\\linewidth}");
    }

    @Test
    void everyMeasuredItemReportsItsKeyAndBothHeights() {
        String measurement = renderer.renderMeasurement(new MeasurementRequest(
                List.of(item("var-1", BULLET), item("var-2", RichContent.plain("Second"))),
                TemplateCustomization.CLASSIC)).value();

        assertThat(measurement)
                .contains("\\typeout{ATOMCOST|var-1|\\the\\ht\\measurebox|\\the\\dp\\measurebox}")
                .contains("\\typeout{ATOMCOST|var-2|\\the\\ht\\measurebox|\\the\\dp\\measurebox}");
    }

    /** EK D.8.1: {@code \mbox} is already a LaTeX command. */
    @Test
    void theMeasurementBoxIsNotNamedAfterAnExistingCommand() {
        String measurement = renderer.renderMeasurement(
                new MeasurementRequest(List.of(item("var-1", BULLET)),
                        TemplateCustomization.CLASSIC)).value();

        assertThat(measurement).doesNotContain("\\newsavebox{\\mbox}");
        assertThat(measurement).contains("\\newsavebox{\\measurebox}");
    }

    // ─── the document itself ───

    @Test
    void marksBecomeCommandsAndTextIsEscaped() {
        String document = renderer.renderFinal(request(TemplateCustomization.CLASSIC)).value();

        assertThat(document)
                .contains("\\resumeItem{Built \\textbf{ETL} pipelines "
                        + "processing \\textbf{300K+ rows}}")
                .contains("\\section*{Experience}")
                // The title, the dates on the right of the first line, and the
                // employer with its place underneath (Bolum 22).
                .contains("\\resumeSubheading{Backend Engineer}{2023-03 – present}"
                        + "{Acme}{İstanbul}");
    }

    /** A contact field says what it is, and links where it can. */
    @Test
    void contactLinesAreLabelledAndLinked() {
        String document = renderer.renderFinal(request(TemplateCustomization.CLASSIC)).value();

        assertThat(document)
                .contains("\\textbf{Email:} \\href{mailto:mustafa@example.com}"
                        + "{\\underline{mustafa@example.com}}")
                .as("a city is not a link")
                .contains("\\textbf{Location:} İstanbul");
    }

    @Test
    void aDocumentIsTheSameBytesEveryTime() {
        var request = request(TemplateCustomization.CLASSIC);

        assertThat(renderer.renderFinal(request).value())
                .isEqualTo(renderer.renderFinal(request).value());
    }

    @Test
    void numbersDoNotFollowTheDefaultLocale() {
        // Absolute rule 7 in its other form: a Turkish locale writes "0,60"
        // for a margin and the document does not compile.
        Locale.setDefault(new Locale("tr", "TR"));

        String document = renderer.renderFinal(request(TemplateCustomization.CLASSIC)).value();

        assertThat(document).contains("margin=0.60in").doesNotContain("margin=0,60in");
    }

    @Test
    void theRendererKnowsNothingAboutSelection() {
        // Bolum 22.2: no atom id, no score, no lock reaches this far. If one
        // ever did, a renderer could start making selection decisions.
        assertThat(RenderRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("header", "sections", "customization", "contentLanguage");
    }

    @Test
    void anUnknownTemplateIsRefused() {
        assertThatThrownBy(() -> renderer.renderFinal(request(new TemplateCustomization(
                "hand-written", FontFamily.SERIF, 10, 0.6, 1.0, HexColor.DEFAULT))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MeasurementRequest.MeasurableItem item(String key, RichContent content) {
        return new MeasurementRequest.MeasurableItem(key, content);
    }

    private static RenderRequest request(TemplateCustomization customization) {
        return new RenderRequest(
                new RenderRequest.ProfileHeader(
                        "Mustafa Tetik",
                        "Backend Engineer",
                        List.of(
                                new RenderRequest.ContactLine("Email", "mustafa@example.com",
                                        "mailto:mustafa@example.com"),
                                new RenderRequest.ContactLine("Location", "İstanbul", ""))),
                List.of(new RenderRequest.RenderableSection(
                        "Experience",
                        SectionLayout.ENTRY_LIST,
                        List.of(new RenderRequest.RenderableEntry(
                                "Backend Engineer", "Acme", "İstanbul", "2023-03 – present",
                                List.of(BULLET))),
                        List.of())),
                customization,
                Locale.ENGLISH);
    }

    private static String preambleOf(String document) {
        int start = document.indexOf("\\begin{document}");
        assertThat(start).as("every document has a body").isPositive();
        return document.substring(0, start);
    }
}
