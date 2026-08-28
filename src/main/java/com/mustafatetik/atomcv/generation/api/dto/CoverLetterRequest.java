package com.mustafatetik.atomcv.generation.api.dto;

import com.mustafatetik.atomcv.generation.coverletter.CoverLetterStyle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Asking for a covering letter, or for another one (Bolum 34.6).
 *
 * <p>Both fields are optional and an empty body is a valid request: write the
 * letter, the ordinary way, with nothing extra known about the employer.
 *
 * @param companyNote Bolum 34.5. What the person themselves knows about this
 *                    company, in their own words — the only source of
 *                    personalisation the letter has. A model asked to admire a
 *                    company it has never heard of writes the paragraph
 *                    everybody can tell was written by a machine
 */
@Schema(description = "A covering letter for a generation that already exists")
public record CoverLetterRequest(

        @Schema(description = "Which of the three variants to write",
                defaultValue = "default")
        CoverLetterStyle style,

        @Schema(description = "What you know about this employer, in your own words. "
                + "Used as given; nothing is inferred from it.",
                example = "They open-sourced their scheduler last year.")
        // Long enough for a paragraph and short enough that it is not a second
        // job description arriving through the back door.
        @Size(max = 2_000)
        String companyNote) {

    public CoverLetterStyle styleOrDefault() {
        return style == null ? CoverLetterStyle.DEFAULT : style;
    }

    public String companyNoteOrBlank() {
        return companyNote == null ? "" : companyNote;
    }
}
