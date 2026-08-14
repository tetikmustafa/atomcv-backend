package com.mustafatetik.atomcv.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * The order of one group of atoms: the atoms of an entry, or the ones hanging
 * straight off a section.
 *
 * <p>Both identifiers are given so the group is named exactly. An id list
 * alone would be ambiguous the moment two entries hold atoms with the same
 * position.
 */
@Schema(name = "AtomReorder")
public record AtomReorderRequest(
        @NotNull UUID sectionId,
        @Schema(description = "Leave out to order the section's own atoms") UUID entryId,
        @NotEmpty @Schema(description = "Every atom of that group, in order") List<UUID> ids) {
}
