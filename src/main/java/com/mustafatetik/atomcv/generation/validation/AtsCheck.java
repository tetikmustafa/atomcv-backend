package com.mustafatetik.atomcv.generation.validation;

import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads our own PDF back and checks the text is still in it (Bolum 23.2).
 *
 * <p>The other half of Bolum 23 was built in Stage 1 and this one was left:
 * {@code FitReport} counts what the <em>selection</em> put on the page, which
 * is a claim about the plan rather than about the artefact. An applicant
 * tracking system does not read the plan.
 *
 * <p><strong>Its own PDFBox call rather than {@code PdfTextExtractor}.</strong>
 * That class is an ingestion component and its contract is a user-facing
 * refusal — an unreadable upload becomes {@code PDF_ENCRYPTED} or an
 * unsupported-format error, which is right for a file somebody chose and wrong
 * for a file we produced ourselves. Here an unreadable PDF is our defect, it is
 * reported and never thrown, and reusing that contract would mean failing a
 * generation the person has already paid for over a diagnostic.
 *
 * <p>Comparison is on a normalised form: extraction re-flows lines, so a bullet
 * printed on two lines comes back with a newline in the middle of a sentence.
 * Whitespace is collapsed and case is folded with {@link Locale#ROOT}, which
 * absolute rule 7 requires — a Turkish default locale would turn "SQL" into
 * "sqı" here and report a bullet as missing from a page that prints it.
 */
public final class AtsCheck {

    private static final Logger log = LoggerFactory.getLogger(AtsCheck.class);

    /**
     * How much of a bullet has to survive to count as found.
     *
     * <p>Not the whole thing: hyphenation and line breaking can drop a
     * character, and a check that demanded every one would report a defect on
     * a page that reads perfectly. Eighty characters is far past the point
     * where a match could be a coincidence between two different bullets.
     */
    private static final int PREFIX = 80;

    private AtsCheck() {
    }

    public static AtsReport of(byte[] pdf, RenderRequest printed) {
        String text = extract(pdf);
        if (text == null) {
            return AtsReport.unreadable();
        }
        String haystack = normalise(text);

        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (var section : printed.sections()) {
            String heading = normalise(section.title());
            if (!heading.isBlank() && haystack.contains(heading)) {
                found.add(section.title());
            } else if (!heading.isBlank()) {
                missing.add(section.title());
            }
        }

        List<String> bullets = bulletsOf(printed);
        int matched = 0;
        for (String bullet : bullets) {
            if (haystack.contains(shorten(normalise(bullet)))) {
                matched++;
            }
        }

        return new AtsReport(found, missing, matched, bullets.size(),
                contactReadable(printed, haystack), inOrder(printed, haystack));
    }

    /**
     * The headings in the order the page printed them, still in that order in
     * the text layer. A two-column template passes every other check here and
     * still interleaves the columns into nonsense; this is what notices.
     */
    private static boolean inOrder(RenderRequest printed, String haystack) {
        // Each heading's own first position, then a check that the positions
        // rise. Searching forward from the previous match instead would report
        // a heading that came back *too early* as missing rather than as out
        // of order — which is the one thing this method exists to notice.
        int previous = -1;
        for (var section : printed.sections()) {
            String heading = normalise(section.title());
            if (heading.isBlank()) {
                continue;
            }
            int at = haystack.indexOf(heading);
            if (at < 0) {
                // Already counted as missing; not also an ordering failure.
                continue;
            }
            if (at <= previous) {
                return false;
            }
            previous = at;
        }
        return true;
    }

    /**
     * A contact line has to come back, or an ATS has a CV it cannot reply to.
     * Any one of them is enough — the address, the phone and the links are
     * printed together and a template that loses one loses all of them.
     */
    private static boolean contactReadable(RenderRequest printed, String haystack) {
        var lines = printed.header().contactLines();
        if (lines.isEmpty()) {
            return true;
        }
        // The value, not the label. The labels are the template's own words
        // and would be found in the extracted text whether or not the address
        // beside them survived, which is the one thing this asks about.
        return lines.stream()
                .map(RenderRequest.ContactLine::value)
                .map(AtsCheck::normalise)
                .filter(line -> !line.isBlank())
                .anyMatch(haystack::contains);
    }

    private static List<String> bulletsOf(RenderRequest printed) {
        List<String> bullets = new ArrayList<>();
        for (var section : printed.sections()) {
            section.atoms().forEach(atom -> bullets.add(atom.plainText()));
            for (var entry : section.entries()) {
                entry.atoms().forEach(atom -> bullets.add(atom.plainText()));
            }
        }
        bullets.removeIf(String::isBlank);
        return bullets;
    }

    private static String shorten(String text) {
        return text.length() <= PREFIX ? text : text.substring(0, PREFIX);
    }

    /** Collapses the line breaking extraction introduces, and folds case. */
    private static String normalise(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    private static String extract(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException | RuntimeException unreadable) {
            // Our own artefact, so this is a defect in the template or the
            // compiler rather than anything the person did. Never the text
            // (absolute rule 4) and never an exception through the pipeline.
            log.warn("A generated PDF could not be read back for the ATS check: {}",
                    unreadable.getClass().getSimpleName());
            return null;
        }
    }
}
