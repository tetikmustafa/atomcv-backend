package com.mustafatetik.atomcv.generation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.GenerationStatus;
import com.mustafatetik.atomcv.generation.validation.MatchLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the history (F-020, Bolum 41.2).
 *
 * <p><strong>Deliberately not {@link GenerationResponse}.</strong> A list of
 * twenty full generations would carry twenty fit reports and twenty covering
 * letters to draw twenty lines of a menu — and the letter is the largest text
 * the row holds. What is here is what a row of a list is read for: when it was
 * made, whether it worked, how long it came out, and how well it matched.
 *
 * <p><strong>Nothing the user pasted, here either.</strong> The posting stays
 * on the row (absolute rule 4), which is the same rule
 * {@link GenerationResponse} keeps and the reason this list has no title on
 * it: every label a history screen would want — the role, the company — is
 * read out of the posting, and publishing one here would be deciding that
 * question by accident. It is asked properly in the handoff instead.
 *
 * @param matchLevel     absent in general mode, where there was no posting to
 *                       be relevant to. Four characters, and the heading the
 *                       full report sits under
 * @param hasCoverLetter whether there is one to open, never the letter itself
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "One generation, as a row of the history")
public record GenerationSummary(
        UUID generationId,
        GenerationStatus status,
        Instant createdAt,

        @Schema(description = "How many pages the compiled document came to; "
                + "absent while it is unfinished or failed")
        Integer pageCount,

        @Schema(description = "The heading over Faz F's counts, absent in general mode")
        MatchLevel matchLevel,

        @Schema(description = "The language the document was written in, as a BCP 47 tag",
                example = "tr")
        String contentLanguage,

        @Schema(description = "Whether a covering letter was written for it")
        boolean hasCoverLetter) {

    public static GenerationSummary of(Generation generation) {
        return new GenerationSummary(
                generation.getId(),
                generation.getStatus(),
                generation.getCreatedAt(),
                generation.getPageCount() == null ? null : generation.getPageCount().intValue(),
                generation.getFitReport() == null ? null : generation.getFitReport().level(),
                blankToNull(generation.getSelectionState() == null
                        ? null : generation.getSelectionState().language()),
                generation.getCoverLetter() != null && !generation.getCoverLetter().isBlank());
    }

    /** Stored as "" rather than null; F-010 settled that "" is worse on the wire. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
