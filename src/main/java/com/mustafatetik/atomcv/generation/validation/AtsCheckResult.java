package com.mustafatetik.atomcv.generation.validation;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the PDF read back as, in the shape it is kept and published in.
 *
 * <p>{@link AtsReport} names the headings it found and missed, and those names
 * are the person's own wording. This keeps the counts and the flags and drops
 * the names, because it is stored on the generation row and sent to a screen
 * (absolute rule 4): "1 of 5 headings did not come back" is the whole of what
 * the person can act on, and an operator reading the row needs no more.
 *
 * <p>The concept's "ATS compatibility check" (§ 10.1) had been running on every
 * document since Stage 2 and was visible only on a counter. This is the record
 * that lets the result screen say so.
 *
 * @param readable        {@link AtsReport#clean()}: every heading, every
 *                        bullet, the contact line and the order came back
 * @param headingsFound   section headings found in the text layer
 * @param headingsExpected section headings the document printed
 * @param bulletsFound    selected wordings found in the text layer
 * @param bulletsExpected selected wordings the document printed
 * @param contactReadable whether the header's contact line survived extraction
 * @param orderPreserved  whether the headings came back in the printed order
 */
@Schema(description = """
        The generated PDF read back as text, the way an applicant tracking
        system reads it. Counts and flags only. Absent on a generation made
        before the check was kept.""")
public record AtsCheckResult(
        boolean readable,
        int headingsFound,
        int headingsExpected,
        int bulletsFound,
        int bulletsExpected,
        boolean contactReadable,
        boolean orderPreserved) {

    public static AtsCheckResult of(AtsReport report) {
        return new AtsCheckResult(
                report.clean(),
                report.headingsFound().size(),
                report.headingsFound().size() + report.headingsMissing().size(),
                report.bulletsFound(),
                report.bulletsExpected(),
                report.contactReadable(),
                report.orderPreserved());
    }
}
