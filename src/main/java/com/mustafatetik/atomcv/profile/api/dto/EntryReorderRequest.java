package com.mustafatetik.atomcv.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * The order of one section's entries.
 *
 * <p>Scoped to a section because that is where the order means anything, and
 * because a list spanning two sections would be a move dressed as a reorder.
 */
@Schema(name = "EntryReorder")
public record EntryReorderRequest(
        @NotNull UUID sectionId,
        @NotEmpty
        @Schema(description = "Every entry of that section, in the order they should appear")
        List<UUID> ids) {
}
