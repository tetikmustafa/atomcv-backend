package com.mustafatetik.atomcv.profile.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mustafatetik.atomcv.profile.domain.Entry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/** One position, degree or project, as the API publishes it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Entry")
public record EntryResponse(
        UUID id,
        UUID sectionId,
        @Schema(example = "Backend Engineer") String title,
        @Schema(example = "Acme") String organization,
        String location,
        LocalDate startDate,
        @Schema(description = "Absent while ongoing") LocalDate endDate,
        String url,
        @Schema(description = "Position within its section, from 0") short displayOrder,
        @Schema(description = "Weight in scoring, 0 to 1", example = "0.5") float importance,
        boolean active,
        @Schema(description = "Selection may not drop it") boolean alwaysInclude,
        @Schema(description = "Never sent for rewriting") boolean verbatim,
        @Schema(description = "Below this many atoms the entry is dropped whole (Bolum 20)")
        short minAtoms,
        @Schema(description = "Send back as If-Match", example = "0") long version) {

    public static EntryResponse of(Entry entry) {
        return new EntryResponse(
                entry.getId(),
                entry.getSectionId(),
                entry.getTitle(),
                entry.getOrganization(),
                entry.getLocation(),
                entry.getStartDate(),
                entry.getEndDate(),
                entry.getUrl(),
                entry.getDisplayOrder(),
                entry.getImportance(),
                entry.isActive(),
                entry.isAlwaysInclude(),
                entry.isVerbatim(),
                entry.getMinAtoms(),
                entry.getVersion() == null ? 0L : entry.getVersion());
    }
}
