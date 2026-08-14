package com.mustafatetik.atomcv.profile.api.dto;

import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A new section. It lands at the end; ordering is a separate operation, so
 * creating one cannot renumber the rest as a side effect.
 */
@Schema(name = "SectionCreate")
public record SectionCreateRequest(
        @NotNull SectionKind kind,
        @NotBlank @Size(max = 120) String title,
        @Schema(description = "Defaults to bullet_list") SectionLayout layout,
        Boolean alwaysInclude,
        Boolean verbatim) {
}
