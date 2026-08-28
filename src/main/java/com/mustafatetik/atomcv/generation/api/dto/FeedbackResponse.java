package com.mustafatetik.atomcv.generation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mustafatetik.atomcv.generation.domain.GenerationFeedback;
import com.mustafatetik.atomcv.generation.domain.SupportGrant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * What was recorded, and what the permission is doing (Bolum 48.4).
 *
 * <p><strong>The grant is echoed back because a consent nobody can check is
 * not a consent.</strong> Bolum 48.4 promises the person can see when their
 * content was actually read; {@code accessedAt} is that promise, and it is
 * null until somebody looks.
 *
 * @param comment deliberately absent. They wrote it, they have it, and
 *                sending it back is a copy of their words travelling for no
 *                reason
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A recorded verdict")
public record FeedbackResponse(
        UUID generationId,
        short rating,
        String category,

        @Schema(description = "The 48-hour diagnostic permission, when there is one")
        Grant contentGrant) {

    /**
     * @param open      whether the content may be read right now — false once
     *                  it is withdrawn or run out, which are different events
     *                  and the timestamps say which
     * @param accessedAt when it was actually used, or absent. The audit trail
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Grant(boolean open, Instant expiresAt, Instant accessedAt,
            Instant revokedAt) {

        static Grant of(SupportGrant grant, Instant now) {
            return new Grant(grant.isOpenAt(now), grant.getExpiresAt(),
                    grant.getAccessedAt(), grant.getRevokedAt());
        }
    }

    public static FeedbackResponse of(
            UUID generationId, GenerationFeedback verdict, SupportGrant grant, Instant now) {

        return new FeedbackResponse(
                generationId, verdict.getRating(), verdict.getCategory(),
                grant == null ? null : Grant.of(grant, now));
    }
}
