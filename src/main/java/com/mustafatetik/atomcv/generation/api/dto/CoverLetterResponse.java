package com.mustafatetik.atomcv.generation.api.dto;

import com.mustafatetik.atomcv.generation.coverletter.CoverLetterStyle;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * The letter that was written (Bolum 34.7).
 *
 * <p>Plain text and no document. Bolum 34.7 renders nothing: a covering letter
 * is pasted into an application form or the body of an email, so a PDF of it
 * would be a file nobody opens. The paragraphs are separated by blank lines
 * and the client decides what to do with them.
 *
 * @param style which variant this is, echoed back so a screen showing three
 *              buttons knows which one it is looking at
 */
@Schema(description = "A covering letter")
public record CoverLetterResponse(
        UUID generationId,

        @Schema(description = "The letter, as plain text with blank lines between parts")
        String coverLetter,

        CoverLetterStyle style) {
}
