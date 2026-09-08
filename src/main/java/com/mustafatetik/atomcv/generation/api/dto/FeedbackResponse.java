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
 * content was actually read.
 *
 * <p><strong>And that half of the promise is not on the wire, because nothing
 * can keep it yet.</strong> {@code support_grants.accessed_at} exists and
 * nothing writes it: reading somebody else's content needs a support-facing
 * path, and absolute rule 3 means there is deliberately none — every read goes
 * through a repository scoped to the owner. A field that is structurally always
 * null is not an audit trail, it is a screen telling the person nobody looked
 * whatever happened, so it is withheld until there is a reader to stamp it.
 * The column stays; the claim comes back with the path (`B-075`).
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
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Grant(boolean open, Instant expiresAt, Instant revokedAt) {

        static Grant of(SupportGrant grant, Instant now) {
            return new Grant(grant.isOpenAt(now), grant.getExpiresAt(), grant.getRevokedAt());
        }
    }

    public static FeedbackResponse of(
            UUID generationId, GenerationFeedback verdict, SupportGrant grant, Instant now) {

        return new FeedbackResponse(
                generationId, verdict.getRating(), verdict.getCategory(),
                grant == null ? null : Grant.of(grant, now));
    }
}
