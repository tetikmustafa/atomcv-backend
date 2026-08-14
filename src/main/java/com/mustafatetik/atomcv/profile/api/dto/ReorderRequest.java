package com.mustafatetik.atomcv.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * The new order, as the complete list of ids.
 *
 * <p>Complete on purpose: a partial list leaves the server guessing where the
 * rest belong, and two clients guessing differently is how an order ends up
 * with two rows claiming the same position. Sending everything also makes the
 * operation idempotent — the same list twice is the same order.
 */
@Schema(name = "Reorder")
public record ReorderRequest(
        @NotEmpty
        @Schema(description = "Every id of the collection, in the order they should appear")
        List<UUID> ids) {
}
