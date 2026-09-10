package com.mustafatetik.atomcv.rendering.docx;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

/**
 * The CV as a Word document (Bolum 22.6).
 *
 * <p><strong>Every case here reads the bytes back rather than inspecting what
 * was written.</strong> A DOCX is a zip of XML parts, and one that a library
 * produced happily can still be one Word refuses to open or an applicant
 * tracking system reads as empty — which would be worse than offering no
 * download at all, because nobody would find out until an employer did.
 */
class DocxDocumentWriterTest {

    private final DocxDocumentWriter writer = new DocxDocumentWriter();

    @Test
    void itproducesAdocumentThatOpens() throws Exception {
        byte[] bytes = writer.write(aCv());

        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertThat(document.getParagraphs()).isNotEmpty();
        }
    }

    /**
     * <strong>The one that matters.</strong> An ATS extracts the text layer,
     * so a document that lays out beautifully and reads back empty is a CV
     * that never reaches a person.
     */
    @Test
    void everyWordIsInTheTextLayer() throws Exception {
        String text = textOf(writer.write(aCv()));

        assertThat(text)
                .contains("Ada Lovelace")
                .contains("ada@example.com")
                .contains("EXPERIENCE")
                .contains("Backend Engineer")
                .contains("Acme Payments")
                .contains("Cut the nightly ledger window")
                .contains("fifty minutes");
    }

    /** Bolum 22.6's own example: a technology or a metric is set bold. */
    @Test
    void amarkedRunIsBoldAndAplainOneIsNot() throws Exception {
        byte[] bytes = writer.write(aCv());

        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            XWPFRun marked = runWithText(document, "fifty minutes");
            XWPFRun plain = runWithText(document, "Cut the nightly ledger window from six hours to ");

            assertThat(marked.isBold()).as("a metric is stressed").isTrue();
            assertThat(plain.isBold()).as("and the sentence around it is not").isFalse();
        }
    }

    /**
     * An unknown mark falls through to plain rather than failing — Bolum
     * 16.2's rule, and the reason {@link Mark} is not an enum. Stored content
     * may carry a mark a newer build wrote.
     */
    @Test
    void anunknownMarkIsPrintedPlainRatherThanRefused() throws Exception {
        var section = new RenderRequest.RenderableSection("Skills", SectionLayout.BULLET_LIST,
                List.of(), List.of(new RichContent(List.of(
                        Run.of("Something", new Mark("sparkle"))))));

        String text = textOf(writer.write(new RenderRequest(
                new RenderRequest.ProfileHeader("Ada", "", List.of()),
                List.of(section), TemplateCustomization.CLASSIC, Locale.ENGLISH)));

        assertThat(text).contains("Something");
    }

    /** An inline section is a label and its row, not a list of bullets. */
    @Test
    void aninlineSectionKeepsItsLabel() throws Exception {
        var section = new RenderRequest.RenderableSection("Tech Stack",
                SectionLayout.INLINE_LIST,
                List.of(new RenderRequest.RenderableEntry("Backend", "", "", "",
                        List.of(RichContent.plain("Go, PostgreSQL, Kafka")))),
                List.of());

        String text = textOf(writer.write(new RenderRequest(
                new RenderRequest.ProfileHeader("Ada", "", List.of()),
                List.of(section), TemplateCustomization.CLASSIC, Locale.ENGLISH)));

        assertThat(text).contains("Backend:").contains("Go, PostgreSQL, Kafka");
    }

    /**
     * The customization travels, which is what makes a compact DOCX dense and
     * a modern one roomy — as closely as a format with no measurement of its
     * own can follow the three templates.
     */
    @Test
    void thecustomizationReachesTheDocument() throws Exception {
        byte[] compact = writer.write(aCvIn(TemplateCustomization.COMPACT));
        byte[] classic = writer.write(aCvIn(TemplateCustomization.CLASSIC));

        try (var dense = new XWPFDocument(new ByteArrayInputStream(compact));
                var roomy = new XWPFDocument(new ByteArrayInputStream(classic))) {

            assertThat(bodyFontSize(dense))
                    .as("compact is set at 10pt and classic at 11")
                    .isLessThan(bodyFontSize(roomy));
        }
    }

    /** An empty profile is an empty document rather than an exception. */
    @Test
    void anemptyCvIsStillAdocument() throws Exception {
        byte[] bytes = writer.write(new RenderRequest(
                new RenderRequest.ProfileHeader("", "", List.of()),
                List.of(), TemplateCustomization.CLASSIC, Locale.ENGLISH));

        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertThat(document.getParagraphs()).isNotNull();
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private static String textOf(byte[] bytes) throws Exception {
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes));
                var extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private static XWPFRun runWithText(XWPFDocument document, String text) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            for (XWPFRun run : paragraph.getRuns()) {
                if (text.equals(run.text())) {
                    return run;
                }
            }
        }
        throw new AssertionError("No run reads exactly: " + text);
    }

    private static int bodyFontSize(XWPFDocument document) {
        // The last paragraph is a bullet, which is body text; the header runs
        // are deliberately larger.
        var paragraphs = document.getParagraphs();
        for (int index = paragraphs.size() - 1; index >= 0; index--) {
            for (XWPFRun run : paragraphs.get(index).getRuns()) {
                if (run.getFontSize() > 0) {
                    return run.getFontSize();
                }
            }
        }
        throw new AssertionError("No run carries a font size");
    }

    private static RenderRequest aCv() {
        return aCvIn(TemplateCustomization.CLASSIC);
    }

    private static RenderRequest aCvIn(TemplateCustomization customization) {
        var bullet = new RichContent(List.of(
                Run.of("Cut the nightly ledger window from six hours to "),
                Run.of("fifty minutes", Mark.METRIC)));
        var entry = new RenderRequest.RenderableEntry(
                "Backend Engineer", "Acme Payments", "Istanbul", "2021 – now", List.of(bullet));
        var section = new RenderRequest.RenderableSection(
                "Experience", SectionLayout.ENTRY_LIST, List.of(entry), List.of());

        return new RenderRequest(
                new RenderRequest.ProfileHeader("Ada Lovelace", "Backend Engineer",
                        List.of(new RenderRequest.ContactLine("email", "ada@example.com", null))),
                List.of(section), customization, Locale.ENGLISH);
    }
}
