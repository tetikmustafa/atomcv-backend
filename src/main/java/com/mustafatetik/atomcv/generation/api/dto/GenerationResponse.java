package com.mustafatetik.atomcv.generation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.GenerationStatus;
import com.mustafatetik.atomcv.generation.validation.FitReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One generation, as the result screen reads it (Bolum 35, F-008).
 *
 * <p>The counts of Faz F live here rather than on the stream. A
 * {@link com.mustafatetik.atomcv.generation.validation.MatchLevel} is four
 * characters and rides the terminal event so the heading appears without a
 * round trip; the report underneath is a document, and a client that missed
 * the stream or reloaded the page would never see it again if this endpoint
 * did not exist.
 *
 * <p><strong>Nothing the user pasted comes back.</strong> The posting is on
 * the row and stays there: it is the largest piece of user content the system
 * holds and no screen asks for it (absolute rule 4).
 *
 * <p>No ETag — Bolum 35.6 keeps them off generations, which are written once
 * and not edited.
 *
 * <p><strong>Two languages, and the client compares them</strong> (F-013).
 * {@code contentLanguage} is what the document was actually written in;
 * {@code postingLanguage} is what the posting was read as. They differ when
 * the profile has no wording for every atom in the posting's language — the
 * CV is written in the profile's own language rather than in two at once —
 * and a screen that says so is the only place a user would learn it.
 *
 * @param fitReport       absent in general mode, where there was no posting to
 *                        be relevant to
 * @param contentLanguage the BCP 47 tag the document was written in
 * @param postingLanguage absent in general mode, and absent when Faz A did not
 *                        name a language for the posting
 * @param coverLetter     Bolum 34's letter when one was written, and absent
 *                        otherwise — it is opt-in, and a letter that could not
 *                        be written honestly is not written at all. Plain
 *                        text: Bolum 34.7 renders no document, because a
 *                        covering letter is pasted into a form or an email
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A generation that was made")
public record GenerationResponse(
        UUID generationId,
        GenerationStatus status,

        @Schema(description = "How many pages the compiled document came to")
        Integer pageCount,

        Instant createdAt,
        FitReport fitReport,

        @Schema(description = "The language the document was written in, as a BCP 47 tag",
                example = "tr")
        String contentLanguage,

        @Schema(description = """
                The language Faz A read the posting as. When it differs from                 contentLanguage the CV was written in the profile's language                 instead: the profile has no wording for every atom in the                 posting's language, and one document is written in one                 language.""",
                example = "en")
        String postingLanguage,

        @Schema(description = "The covering letter, as plain text with blank lines "
                + "between its parts")
        String coverLetter,

        @Schema(description = "What this person already said about it, and the "
                + "48-hour diagnostic permission if they opened one. Absent "
                + "when they have not judged it.")
        FeedbackResponse feedback) {

    /**
     * <strong>The same type the feedback endpoint answers with</strong>, not a
     * second one shaped like it (F-019).
     *
     * <p>A screen that has just recorded a verdict and a screen that has just
     * loaded one are showing the same thing, and two types would be two ICU
     * bindings drifting apart at the first change. The {@code generationId}
     * inside is redundant here and kept anyway: the price of it is a duplicated
     * uuid, and the price of removing it is a second schema.
     */
    public static GenerationResponse of(Generation generation) {
        return of(generation, null);
    }

    public static GenerationResponse of(Generation generation, FeedbackResponse feedback) {
        return new GenerationResponse(
                generation.getId(),
                generation.getStatus(),
                generation.getPageCount() == null ? null : generation.getPageCount().intValue(),
                generation.getCreatedAt(),
                generation.getFitReport(),
                blankToNull(generation.getSelectionState() == null
                        ? null : generation.getSelectionState().language()),
                blankToNull(generation.getJdAnalysis() == null
                        ? null : generation.getJdAnalysis().jdLanguage()),
                blankToNull(generation.getCoverLetter()),
                feedback);
    }

    /**
     * Both fields are stored as "" rather than null — {@code StoredSelection}
     * and {@code JobAnalysis} normalise them that way — and F-010 settled that
     * an empty string on the wire is worse than an absent field: a client
     * cannot tell it from a language whose name is blank.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
