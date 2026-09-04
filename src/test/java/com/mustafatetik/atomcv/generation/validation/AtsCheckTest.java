package com.mustafatetik.atomcv.generation.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/**
 * Bolum 23.2's reader, against PDFs built here rather than compiled.
 *
 * <p>A real XeLaTeX round trip is the case that matters and it lives in the
 * latex lane, which takes minutes. What is under test here is the comparison:
 * extraction re-flows lines and folds nothing, so the interesting failures are
 * a bullet broken across two lines, a heading that never made it, and columns
 * that come back interleaved. Those are all reproducible with a PDF written by
 * hand, and none of them needs a compiler.
 */
class AtsCheckTest {

    @Test
    void aPageThatCarriesEverythingReadsClean() {
        var request = request(List.of("Experience"), List.of("Moved 300K rows with Fabric"));
        byte[] pdf = pdf("ben@example.com", "Experience", "Moved 300K rows with Fabric");

        AtsReport report = AtsCheck.of(pdf, request);

        assertThat(report.clean()).isTrue();
        assertThat(report.headingsFound()).containsExactly("Experience");
        assertThat(report.bulletsFound()).isEqualTo(report.bulletsExpected()).isEqualTo(1);
    }

    /**
     * The failure this whole check exists for: a page that looks right and
     * whose text layer is empty. Nothing upstream can see it — the budget, the
     * fit report and the validators all measure the CV before it is a PDF.
     */
    @Test
    void aPageWithNoTextLayerIsNotClean() {
        var request = request(List.of("Experience"), List.of("Moved 300K rows with Fabric"));

        AtsReport report = AtsCheck.of(pdf("", ""), request);

        assertThat(report.clean()).isFalse();
        assertThat(report.headingsMissing()).containsExactly("Experience");
        assertThat(report.bulletsFound()).isZero();
    }

    /**
     * Extraction breaks a printed line wherever the page did, so a bullet
     * comes back with a newline in the middle of a sentence. Comparing raw
     * would report every long bullet as missing from a page that prints it.
     */
    @Test
    void aBulletBrokenAcrossLinesIsStillFound() {
        String bullet = "Moved three hundred thousand rows nightly with Microsoft Fabric "
                + "and cut the batch from four hours to forty minutes";
        var request = request(List.of("Experience"), List.of(bullet));

        // Two text lines, exactly as a wrapped bullet arrives.
        byte[] pdf = pdf("ben@example.com", "Experience",
                "Moved three hundred thousand rows nightly with Microsoft Fabric",
                "and cut the batch from four hours to forty minutes");

        assertThat(AtsCheck.of(pdf, request).bulletsFound()).isEqualTo(1);
    }

    /** A two-column template can carry every word and still interleave them. */
    @Test
    void headingsThatComeBackOutOfOrderAreReported() {
        var request = request(List.of("Experience", "Education"), List.of());
        byte[] pdf = pdf("ben@example.com", "Education", "Experience");

        AtsReport report = AtsCheck.of(pdf, request);

        assertThat(report.headingsMissing()).isEmpty();
        assertThat(report.orderPreserved()).isFalse();
        assertThat(report.clean()).isFalse();
    }

    /** An ATS with a CV it cannot reply to has a CV it cannot use. */
    @Test
    void aLostContactLineIsReported() {
        var request = request(List.of("Experience"), List.of());
        byte[] pdf = pdf("", "Experience");

        assertThat(AtsCheck.of(pdf, request).contactReadable()).isFalse();
    }

    /** Our own artefact being unreadable is a defect to report, never to throw. */
    @Test
    void somethingThatIsNotAPdfIsReportedRatherThanThrown() {
        var request = request(List.of("Experience"), List.of());

        AtsReport report = AtsCheck.of("not a pdf".getBytes(), request);

        assertThat(report.clean()).isFalse();
        assertThat(report.bulletsExpected()).isZero();
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private static RenderRequest request(List<String> headings, List<String> bullets) {
        List<RenderRequest.RenderableSection> sections = headings.stream()
                .map(heading -> new RenderRequest.RenderableSection(heading,
                        SectionLayout.BULLET_LIST, List.of(),
                        // The bullets sit under the first heading; the second
                        // exists only so that order can be wrong.
                        heading.equals(headings.get(0))
                                ? bullets.stream().map(RichContent::plain).toList()
                                : List.of()))
                .toList();
        return new RenderRequest(
                new RenderRequest.ProfileHeader("Mustafa Tetik", "Backend Engineer",
                        List.of(new RenderRequest.ContactLine(
                                "Email", "ben@example.com", "mailto:ben@example.com"))),
                sections, TemplateCustomization.CLASSIC, java.util.Locale.ENGLISH);
    }

    /** One page, one line per argument, in a font PDFBox can always extract. */
    private static byte[] pdf(String contact, String... lines) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.newLineAtOffset(50, 750);
                if (!contact.isBlank()) {
                    content.showText(contact);
                    content.newLineAtOffset(0, -16);
                }
                for (String line : lines) {
                    if (line.isBlank()) {
                        continue;
                    }
                    content.showText(line);
                    content.newLineAtOffset(0, -16);
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
