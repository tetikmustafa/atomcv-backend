package com.mustafatetik.atomcv.shared.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * The error body, for the published schema (Bolum 35.4).
 *
 * <p>Responses are produced by {@link ProblemDetailAdvice}, not by this record.
 * It exists so that the generated OpenAPI document carries the two closed
 * vocabularies — the frontend generates its types from that document, and an
 * enum that only appears in prose cannot be generated from.
 *
 * <p>A test compares the fields here against a body the advice actually
 * produces, so the two cannot drift apart quietly.
 */
@Schema(name = "ApiError", description = "RFC 7807 problem detail with a translatable code")
public record ApiErrorResponse(

        @Schema(description = "Relative reference identifying the problem type",
                example = "/errors/conflicting-preferences")
        String type,

        @Schema(description = "Developer-facing English, stable across occurrences. Never displayed.",
                example = "Conflicting preferences")
        String title,

        @Schema(description = "HTTP status", example = "409")
        int status,

        @Schema(description = "The path that produced it", example = "/api/v1/generations")
        String instance,

        @Schema(description = "Translation key: the client resolves errors.{CODE}")
        ErrorCode code,

        @Schema(description = "Values the translated message interpolates. Keys and types are "
                + "fixed per code; the server refuses to publish anything undeclared.",
                example = "{\"pinnedPages\": 2.3, \"maxPages\": 1}")
        Map<String, Object> params,

        @Schema(description = "What the user can do about it. The server owns this list.")
        List<Resolution> resolutions) {
}
