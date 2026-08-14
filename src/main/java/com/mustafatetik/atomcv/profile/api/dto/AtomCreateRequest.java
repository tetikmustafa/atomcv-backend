package com.mustafatetik.atomcv.profile.api.dto;

import com.mustafatetik.atomcv.profile.domain.AtomKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * A new atom, with the text it exists to hold.
 *
 * <p>Content is required. An atom without a wording is a fact nobody can read:
 * the renderer has nothing to print and the measurement has nothing to
 * measure, so creating the two together keeps that state from existing at all.
 */
@Schema(name = "AtomCreate")
public record AtomCreateRequest(
        @NotNull UUID sectionId,
        @Schema(description = "Leave out for an atom that hangs straight off the section")
        UUID entryId,
        @NotNull AtomKind kind,
        @NotNull @Valid ContentDto content,
        @Schema(description = "Language of this first wording", example = "en")
        @Size(min = 2, max = 16) String language,
        @DecimalMin("0.0") @DecimalMax("1.0") Float importance,
        Boolean alwaysInclude,
        Boolean verbatim,
        List<@Size(max = 80) String> skills,
        List<@Size(max = 80) String> metrics,
        List<@Size(max = 120) String> properNouns) {
}
