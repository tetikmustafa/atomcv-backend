package com.mustafatetik.atomcv.generation.validation;

import java.util.List;

/**
 * Whether the page we just produced can be read back as text (Bolum 23.2).
 *
 * <p>Every other check in this product measures the CV before it is a PDF —
 * the budget, the fit report, the validators. This one is the only one that
 * measures the artefact itself, and it is the only one that can catch the
 * failure the product exists to avoid: a beautiful page that an applicant
 * tracking system reads as an empty document. A font that ships no text layer,
 * a template that lays a column out with absolute positioning, a ligature that
 * strips to nothing — none of those change a single number upstream.
 *
 * <p><strong>It reports; it never refuses.</strong> A page that has already
 * been paid for and fits its limit is the person's document. Anything here is
 * a defect in <em>our</em> template or fonts, not in their CV, and taking the
 * document away from them would be answering our own bug with their loss.
 * Bolum 23.2 puts this in the report beside the coverage counts, and the
 * counters are what an operator watches.
 *
 * @param headingsFound   section headings that came back out of the PDF
 * @param headingsMissing headings the document printed and the text layer does
 *                        not have. Non-empty is a template or font defect
 * @param bulletsFound    how many of the selected wordings were found
 * @param bulletsExpected how many were printed
 * @param contactReadable whether the header's contact line survived extraction
 * @param orderPreserved  whether the headings come back in the order they were
 *                        printed in. A two-column template can pass every other
 *                        check and still interleave two columns into nonsense
 */
public record AtsReport(
        List<String> headingsFound,
        List<String> headingsMissing,
        int bulletsFound,
        int bulletsExpected,
        boolean contactReadable,
        boolean orderPreserved) {

    public AtsReport {
        headingsFound = List.copyOf(headingsFound);
        headingsMissing = List.copyOf(headingsMissing);
    }

    /** Nothing was checked — the reader could not open what the compiler produced. */
    public static AtsReport unreadable() {
        return new AtsReport(List.of(), List.of(), 0, 0, false, false);
    }

    /**
     * Whether the page reads cleanly enough to hand to a machine.
     *
     * <p>Every heading present, every bullet found, the contact line readable
     * and the order intact. Deliberately strict: this measures our own output
     * against our own input, so anything less than everything is a defect
     * somebody should look at rather than a threshold to tune.
     */
    public boolean clean() {
        return headingsMissing.isEmpty()
                && bulletsExpected == bulletsFound
                && contactReadable
                && orderPreserved;
    }

    /**
     * Shape only. A section heading is the user's own wording — absolute rule
     * 4 applies to it exactly as it does to a bullet, and this record is the
     * one thing here that reaches a log line.
     */
    @Override
    public String toString() {
        return "AtsReport[headings=" + headingsFound.size() + "/"
                + (headingsFound.size() + headingsMissing.size())
                + ", bullets=" + bulletsFound + "/" + bulletsExpected
                + ", contact=" + contactReadable + ", order=" + orderPreserved + "]";
    }
}
