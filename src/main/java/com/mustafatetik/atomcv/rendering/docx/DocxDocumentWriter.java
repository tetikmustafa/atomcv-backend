package com.mustafatetik.atomcv.rendering.docx;

import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.List;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;

/**
 * The same CV as a Word document (Bolum 22.6).
 *
 * <p><strong>Not a {@code DocumentRenderer}, and that is not an oversight.</strong>
 * That interface returns a {@code RenderedSource} — a string a compiler turns
 * into a document — and it carries a {@code capacity} because the string has
 * to be measured before selection can promise a page. Neither applies here:
 * POI <em>is</em> the document, and nothing measures it.
 *
 * <p><strong>Which is why the page guarantee is approximate here and exact in
 * the PDF.</strong> Bolum 22.6 says so and this does not pretend otherwise. A
 * DOCX is a second rendering of a generation that was already selected against
 * a LaTeX page: the atoms are the ones that fit there, and Word may set them
 * in a little more or a little less room. It is the same content, not a second
 * promise — {@code B-094} is where the frontend is told to say so.
 *
 * <p>Read straight off {@code content_snapshot}, like the PDF download. Not
 * from today's profile: the person may have edited a bullet since, and a
 * document that came back different from the one they sent an employer would
 * be worse than no document at all (EK D.6.3).
 *
 * <p>Marks are semantic and this decides what they look like, the same way the
 * LaTeX renderer does: technology, metric and emphasis are set bold, a link is
 * left as its text. An unknown mark falls through to plain — Bolum 16.2's rule,
 * and the reason {@link Mark} is not an enum.
 */
@Component
public class DocxDocumentWriter {

    /**
     * Twips: 1440 to the inch, which is how Word states a margin.
     *
     * <p>Every measurement below is derived from the customization, so a DOCX
     * of a compact CV is dense and one of a modern CV is roomy — the same
     * three templates, as closely as a format with no measurement of its own
     * can follow them.
     */
    private static final int TWIPS_PER_INCH = 1440;

    public byte[] write(RenderRequest request) {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {

            margins(document, request.customization());
            header(document, request);
            for (RenderRequest.RenderableSection section : request.sections()) {
                section(document, section, request.customization());
            }
            document.write(bytes);
            return bytes.toByteArray();
        } catch (IOException impossible) {
            // A ByteArrayOutputStream does not fail, and neither does POI
            // writing into one. Wrapped rather than swallowed: if it ever
            // does, it is a defect and not a document.
            throw new UncheckedIOException(impossible);
        }
    }

    /** The same margin the PDF was set at, in the unit Word states it in. */
    private static void margins(XWPFDocument document, TemplateCustomization customization) {
        CTSectPr section = document.getDocument().getBody().addNewSectPr();
        var page = section.addNewPgMar();
        BigInteger margin = BigInteger.valueOf(
                Math.round(customization.marginInches() * TWIPS_PER_INCH));
        page.setTop(margin);
        page.setBottom(margin);
        page.setLeft(margin);
        page.setRight(margin);
    }

    private static void header(XWPFDocument document, RenderRequest request) {
        RenderRequest.ProfileHeader profile = request.header();
        TemplateCustomization customization = request.customization();

        XWPFParagraph name = document.createParagraph();
        name.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = name.createRun();
        style(run, customization);
        run.setBold(true);
        // The name at roughly the size \Huge sets it at, which is what a
        // reader uses to find the top of the page.
        run.setFontSize((int) Math.round(customization.fontSizePt() * 2));
        run.setText(profile.name());

        String contact = profile.contactLines().stream()
                .map(RenderRequest.ContactLine::value)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "  ·  " + right)
                .orElse("");
        if (!contact.isBlank()) {
            XWPFParagraph line = document.createParagraph();
            line.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun contactRun = line.createRun();
            style(contactRun, customization);
            contactRun.setText(contact);
        }
    }

    private static void section(XWPFDocument document,
            RenderRequest.RenderableSection section, TemplateCustomization customization) {

        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(160);
        // A rule under the heading, which is what all three templates draw.
        // Word has no \titlerule; a bottom border on the paragraph is the same
        // line, and it takes the accent colour the same way.
        heading.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.SINGLE);
        XWPFRun run = heading.createRun();
        style(run, customization);
        run.setBold(true);
        run.setFontSize((int) Math.round(customization.fontSizePt() + 2));
        run.setText(section.title().toUpperCase(java.util.Locale.ROOT));

        if (section.layout() == SectionLayout.INLINE_LIST) {
            inline(document, section, customization);
            return;
        }

        for (RichContent atom : section.atoms()) {
            bullet(document, atom, customization, section.layout() != SectionLayout.PARAGRAPH);
        }
        for (RenderRequest.RenderableEntry entry : section.entries()) {
            entry(document, entry, customization);
        }
    }

    private static void entry(XWPFDocument document,
            RenderRequest.RenderableEntry entry, TemplateCustomization customization) {

        XWPFParagraph title = document.createParagraph();
        title.setSpacingBefore(80);
        XWPFRun run = title.createRun();
        style(run, customization);
        run.setBold(true);
        run.setText(entry.title());
        if (entry.dateRange() != null && !entry.dateRange().isBlank()) {
            // Word's tab stops would need a table to right-align this the way
            // the PDF does. An en dash keeps it on one line and readable, and
            // an ATS reads the same words either way.
            XWPFRun dates = title.createRun();
            style(dates, customization);
            dates.setText("  —  " + entry.dateRange());
        }

        String below = org.springframework.util.StringUtils.hasText(entry.organization())
                ? entry.organization()
                : "";
        if (org.springframework.util.StringUtils.hasText(entry.location())) {
            below = below.isBlank() ? entry.location() : below + ", " + entry.location();
        }
        if (!below.isBlank()) {
            XWPFParagraph where = document.createParagraph();
            XWPFRun whereRun = where.createRun();
            style(whereRun, customization);
            whereRun.setItalic(true);
            whereRun.setText(below);
        }

        for (RichContent atom : entry.atoms()) {
            bullet(document, atom, customization, true);
        }
    }

    private static void inline(XWPFDocument document,
            RenderRequest.RenderableSection section, TemplateCustomization customization) {

        for (RenderRequest.RenderableEntry entry : section.entries()) {
            XWPFParagraph row = document.createParagraph();
            XWPFRun label = row.createRun();
            style(label, customization);
            label.setBold(true);
            label.setText(entry.title() + ": ");
            for (RichContent atom : entry.atoms()) {
                writeRuns(row, atom, customization);
            }
        }
        for (RichContent atom : section.atoms()) {
            XWPFParagraph row = document.createParagraph();
            writeRuns(row, atom, customization);
        }
    }

    private static void bullet(XWPFDocument document, RichContent content,
            TemplateCustomization customization, boolean bulleted) {

        XWPFParagraph paragraph = document.createParagraph();
        if (bulleted) {
            // A literal bullet and a hanging indent rather than a numbering
            // definition. Word's list machinery is a separate part inside the
            // package, and an ATS reading the text layer sees the same
            // character either way -- which is the only reader that matters
            // here (Bolum 22.6).
            paragraph.setIndentationLeft(360);
            paragraph.setIndentationHanging(180);
            XWPFRun marker = paragraph.createRun();
            style(marker, customization);
            marker.setText("•  ");
        }
        writeRuns(paragraph, content, customization);
    }

    /**
     * One run per mark, which is what makes a DOCX searchable rather than a
     * picture of a CV: the words stay words and only their weight changes.
     */
    private static void writeRuns(XWPFParagraph paragraph, RichContent content,
            TemplateCustomization customization) {

        for (Run run : content.runs()) {
            XWPFRun word = paragraph.createRun();
            style(word, customization);
            word.setBold(isBold(run.marks()));
            word.setText(run.text());
        }
    }

    /**
     * Bolum 22.6's own example: a technology or a metric is set bold. Emphasis
     * joins them because the rewrite marks what it wants stressed, and an
     * unknown mark falls through to plain rather than failing (Bolum 16.2).
     */
    private static boolean isBold(List<Mark> marks) {
        return marks.contains(Mark.TECHNOLOGY)
                || marks.contains(Mark.METRIC)
                || marks.contains(Mark.EMPHASIS);
    }

    private static void style(XWPFRun run, TemplateCustomization customization) {
        run.setFontFamily(wordFont(customization.fontFamily()));
        run.setFontSize((int) Math.round(customization.fontSizePt()));
    }

    /**
     * The nearest thing Word has, by name.
     *
     * <p>A DOCX names a font and does not carry it, so this is a request
     * rather than a guarantee: whatever the reader has installed is what they
     * see. Three families that exist on every desktop, which is the whole
     * reason not to name the ones the PDF embeds.
     */
    private static String wordFont(FontFamily family) {
        return switch (family) {
            case SANS -> "Arial";
            case SERIF -> "Georgia";
            case BOOK -> "Palatino Linotype";
            case MODERN -> "Calibri";
        };
    }
}
