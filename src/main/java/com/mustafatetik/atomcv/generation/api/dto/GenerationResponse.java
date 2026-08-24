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
 * @param fitReport absent in general mode, where there was no posting to be
 *                  relevant to
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A generation that was made")
public record GenerationResponse(
        UUID generationId,
        GenerationStatus status,

        @Schema(description = "How many pages the compiled document came to")
        Integer pageCount,

        Instant createdAt,
        FitReport fitReport) {

    public static GenerationResponse of(Generation generation) {
        return new GenerationResponse(
                generation.getId(),
                generation.getStatus(),
                generation.getPageCount() == null ? null : generation.getPageCount().intValue(),
                generation.getCreatedAt(),
                generation.getFitReport());
    }
}
