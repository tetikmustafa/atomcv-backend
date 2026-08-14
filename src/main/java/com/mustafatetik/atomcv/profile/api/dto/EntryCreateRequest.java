package com.mustafatetik.atomcv.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** A new entry. It lands at the end of its section. */
@Schema(name = "EntryCreate")
public record EntryCreateRequest(
        @NotNull UUID sectionId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String organization,
        @Size(max = 120) String location,
        LocalDate startDate,
        @Schema(description = "Leave out while ongoing") LocalDate endDate,
        @Size(max = 300) String url,
        @DecimalMin("0.0") @DecimalMax("1.0") Float importance,
        Boolean alwaysInclude,
        Boolean verbatim,
        @Schema(description = "Defaults to 2") Short minAtoms) {
}
