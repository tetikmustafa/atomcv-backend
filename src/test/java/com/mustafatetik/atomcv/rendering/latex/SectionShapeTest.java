package com.mustafatetik.atomcv.rendering.latex;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import com.mustafatetik.atomcv.rendering.model.MeasurementRequest;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The two shapes the canonical template gives a section that is not a list of
 * bullets (Bolum 33.4).
 *
 * <p>Both were wrong on a real CV and neither was covered. A summary came out
 * as a bulleted item — a marker in front of a paragraph — and a Tech Stack came
 * out as unbroken lines of comma-separated words with no label to scan down.
 * The reference document sets the first in a label-less list and the second as
 * {@code \textbf{Category}{: item, item}}, and these say so.
 */
class SectionShapeTest {

    private final LatexDocumentRenderer renderer = new LatexDocumentRenderer();

    // ── About: one paragraph, whatever its length ─────────────────────────

    /**
     * Four sentences and 380 characters, and still one {@code \resumeItem}.
     *
     * <p>The length is the point. "One paragraph" has to hold for a summary
     * long enough that a renderer might be tempted to break it, because that is
     * what a real About is — the shortest of the four in the profile behind
     * this is 506 characters.
     */
    @Test
    void aSummaryIsOneParagraphWithNoBulletInFrontOfIt() {
        String body = bodyOf(oneSection(new RenderRequest.RenderableSection(
                "About", SectionLayout.PARAGRAPH, List.of(), List.of(SUMMARY))));

        assertThat(body)
                .contains("\\section*{About}\n"
                        + "\\resumeParagraphListStart\n"
                        + "\\resumeItem{" + SUMMARY.plainText() + "}\n"
                        + "\\resumeParagraphListEnd\n")
                .as("a paragraph is never set as a bulleted item")
                .doesNotContain("\\resumeItemListStart")
                .doesNotContain("\\begin{itemize}");
    }

    /**
     * A summary written into an entry still prints as a paragraph.
     *
     * <p>Extraction has to put every atom somewhere and the shape it is given
     * has only entries, so it invents a title for the one it makes — and a real
     * import produced <em>Professional Summary</em>. {@code ProfileWriter} hangs
     * a summary off its section now, but a row written before it did, or by
     * another client, must not put a heading nobody wrote above the paragraph.
     */
    @Test
    void aSummaryInAnEntryPrintsNoHeadingForIt() {
        String body = bodyOf(oneSection(new RenderRequest.RenderableSection(
                "About", SectionLayout.PARAGRAPH,
                List.of(new RenderRequest.RenderableEntry(
                        "Professional Summary", "", "", "", List.of(SUMMARY))),
                List.of())));

        assertThat(body)
                .contains("\\resumeItem{" + SUMMARY.plainText() + "}")
                .doesNotContain("Professional Summary")
                .doesNotContain("\\resumeProjectHeading");
    }

    // ── Tech Stack: a bold label, then the list it introduces ─────────────

    @Test
    void anInlineRowIsABoldLabelAndThenItsItems() {
        assertThat(bodyOf(techStack()))
                .contains("\\resumeInlineList{\n"
                        + "\\textbf{Programming Languages}{: Java, Python, SQL} \\\\\n"
                        + "\\textbf{Backend \\& Microservices}{: Spring Boot, Hibernate}\n"
                        + "}\n");
    }

    /** And the same rule reaches Languages, which is the row it is named for. */
    @Test
    void aLanguageIsALabelAndALevel() {
        String body = bodyOf(oneSection(new RenderRequest.RenderableSection(
                "Languages", SectionLayout.INLINE_LIST, List.of(),
                List.of(RichContent.plain("Turkish: Native")))));

        assertThat(body).contains("\\textbf{Turkish}{: Native}");
    }

    /**
     * A row with no label is printed as it stands.
     *
     * <p>Nothing is invented and nothing is guessed: the bold marks where a
     * label the row already carried starts and stops, so a row carrying none
     * gets no bold at all — a bare list of skills, and a sentence whose colon
     * arrives too late to be a heading anyone scans down.
     */
    @Test
    void aRowWithNoLabelIsLeftAlone() {
        String body = bodyOf(oneSection(new RenderRequest.RenderableSection(
                "Tech Stack", SectionLayout.INLINE_LIST, List.of(),
                List.of(RichContent.plain("Java, Python, SQL"),
                        RichContent.plain("A sentence long enough that its colon lands well "
                                + "past anything a reader scans down: like this one")))));

        assertThat(body)
                .contains("\\resumeInlineList{\nJava, Python, SQL \\\\\n")
                .as("a colon sixty characters in is not a label")
                .doesNotContain("\\textbf{A sentence long enough");
    }

    /**
     * The items are set plain, whatever extraction marked in them.
     *
     * <p>A list whose every entry is a technology comes back with most of the
     * row marked: the real Tech Stack behind this arrived with seventy per cent
     * of each line emphasised, which sets seventy per cent of a skills matrix
     * in italic and emphasises none of it. The label is the emphasis in a row
     * like this, and the reference document sets the items after it plain.
     */
    @Test
    void theItemsAfterTheLabelAreSetPlain() {
        String body = bodyOf(oneSection(new RenderRequest.RenderableSection(
                "Tech Stack", SectionLayout.INLINE_LIST, List.of(),
                List.of(new RichContent(List.of(
                        Run.of("Backend: "),
                        Run.of("Spring Boot", Mark.TECHNOLOGY),
                        Run.of(", "),
                        Run.of("Hibernate", Mark.EMPHASIS)))))));

        assertThat(body)
                .contains("\\textbf{Backend}{: Spring Boot, Hibernate}")
                .doesNotContain("\\textit{Hibernate}");
    }

    /**
     * The label is set once. A marked label came out bold italic where the
     * reference document has bold: the bold is already the emphasis, and
     * emphasising the emphasis says nothing twice.
     */
    @Test
    void aMarkedLabelIsStillOnlyBold() {
        String body = bodyOf(oneSection(new RenderRequest.RenderableSection(
                "Languages", SectionLayout.INLINE_LIST, List.of(),
                List.of(new RichContent(List.of(
                        Run.of("Turkish", Mark.EMPHASIS),
                        Run.of(": Native")))))));

        assertThat(body)
                .contains("\\textbf{Turkish}{: Native}")
                .doesNotContain("\\textit{Turkish}");
    }

    // ── and the measurement sees the same row ─────────────────────────────

    /**
     * Bolum 22.4's third rule, for the shape this file added.
     *
     * <p>A bold label is wider than a plain one. Measured without it, every
     * skills matrix reports a row narrower than the one that reaches the page —
     * wrong in the one direction a render cost may never be wrong in, because
     * the page guarantee is built on the number being an upper bound of what is
     * printed rather than a lower one.
     */
    @Test
    void anInlineRowIsMeasuredTheWayItIsPrinted() {
        var row = RichContent.plain("Programming Languages: Java, Python, SQL");

        String measurement = renderer.renderMeasurement(new MeasurementRequest(
                List.of(new MeasurementRequest.MeasurableItem(
                                "inline", row, CapacityModel.RowShape.INLINE_ROW_SHAPE),
                        new MeasurementRequest.MeasurableItem(
                                "bullet", row, CapacityModel.RowShape.ENTRY_BULLET)),
                TemplateCustomization.CLASSIC)).value();

        assertThat(measurement)
                .contains("\\parbox{\\linewidth}{\\raggedright \\textbf{Programming Languages}"
                        + "{: Java, Python, SQL}}")
                .as("every other shape is measured as the plain text it prints")
                .contains("\\parbox{\\linewidth}{\\raggedright "
                        + "Programming Languages: Java, Python, SQL}");
    }

    /**
     * The measurement box breaks its lines the way the page breaks them.
     *
     * <p>{@code \parbox} does not inherit the paragraph shape around it: LaTeX
     * runs {@code \@parboxrestore} on the way in, which sets {@code \rightskip}
     * to zero and hands back a <em>justified</em> box. The page is
     * {@code \raggedright}, and the difference is not cosmetic — a justified
     * line may shrink eighteen interword spaces by a third each to avoid
     * breaking, and a ragged one may not. A bullet a few points too long was
     * measured at one line and set at two; forty of them turned a one-page
     * promise into a two-page PDF.
     */
    @Test
    void themeasurementBoxBreaksItsLinesTheWayThePageDoes() {
        String measurement = renderer.renderMeasurement(new MeasurementRequest(
                List.of(new MeasurementRequest.MeasurableItem("bullet", SUMMARY)),
                TemplateCustomization.CLASSIC)).value();

        assertThat(measurement)
                .as("the box is ragged-right, like the page")
                .contains("\\parbox{\\linewidth}{\\raggedright ");
        assertThat(renderer.renderCalibration(TemplateCustomization.CLASSIC).value())
                .as("and the page it is calibrated against says so in the preamble")
                .contains("\\raggedright");
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private static final RichContent SUMMARY = RichContent.plain(
            "Computer Engineering graduate specializing in Distributed Systems and Backend "
            + "Development. Architected scalable microservices with Java 21, Spring Boot and "
            + "Spring Cloud, orchestrating five-container deployments via Docker Compose. "
            + "Experienced in applying TDD and standardizing RESTful APIs. An agile and rapid "
            + "learner, passionate about exploring new architectural patterns.");

    private static RenderRequest techStack() {
        return oneSection(new RenderRequest.RenderableSection(
                "Tech Stack", SectionLayout.INLINE_LIST,
                List.of(new RenderRequest.RenderableEntry(
                                "Programming Languages", "", "", "",
                                List.of(RichContent.plain(
                                        "Programming Languages: Java, Python, SQL"))),
                        new RenderRequest.RenderableEntry(
                                "Backend & Microservices", "", "", "",
                                List.of(RichContent.plain(
                                        "Backend & Microservices: Spring Boot, Hibernate")))),
                List.of()));
    }

    private static RenderRequest oneSection(RenderRequest.RenderableSection section) {
        return new RenderRequest(
                new RenderRequest.ProfileHeader("Mustafa Tetik", null, List.of()),
                List.of(section), TemplateCustomization.CLASSIC, Locale.ENGLISH);
    }

    /**
     * The document without its preamble.
     *
     * <p>Every command asserted absent here is defined up there, so a
     * {@code doesNotContain} against the whole string would pass or fail on the
     * template rather than on the page.
     */
    private String bodyOf(RenderRequest request) {
        String document = renderer.renderFinal(request).value();
        return document.substring(document.indexOf("\\begin{document}"));
    }
}
