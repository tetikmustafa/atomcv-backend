package com.mustafatetik.atomcv.generation.api.dto;

import com.mustafatetik.atomcv.generation.coverletter.CoverLetterStyle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Asking for a covering letter, or for another one.
 *
 * <p>Every field is optional and an empty body is a valid request: write the
 * letter, the ordinary way, with nothing extra known about the employer.
 *
 * @param companyNote what the person themselves knows about this
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
        String companyNote,

        // F-045: the concept document promises the CV and the letter a
        // language each (§ 10.1), and until this field the letter's came only
        // from the profile's default. Its own field rather than the CV's: a
        // Turkish CV and an English letter for an English posting are both
        // right (CoverLetterWriter.languageFor).
        @Schema(description = "The letter's language, as an ISO 639-1 code — one of "
                + "`capabilities.allowedLanguages`. Omitted, the profile's "
                + "`defaults.coverLetterLanguage` decides, and its `auto` follows the posting.",
                example = "en", pattern = "^[a-z]{2}$")
        @Pattern(regexp = "^[a-z]{2}$")
        String language) {

    /** The ordinary request: default style, no note, the profile's language. */
    public static final CoverLetterRequest EMPTY = new CoverLetterRequest(null, null, null);

    public CoverLetterStyle styleOrDefault() {
        return style == null ? CoverLetterStyle.DEFAULT : style;
    }

    public String companyNoteOrBlank() {
        return companyNote == null ? "" : companyNote;
    }
}
