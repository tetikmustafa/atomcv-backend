package com.mustafatetik.atomcv.profile.api.dto;

import com.mustafatetik.atomcv.profile.domain.AtomKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The controls on an atom (Bolum 35.2 calls this endpoint exactly that).
 *
 * <p>No content here. Text belongs to a wording, and a wording is a resource
 * with its own version — editing a sentence through the atom would make two
 * rows share one precondition.
 */
@Schema(name = "AtomPatch")
public record AtomPatchRequest(
        AtomKind kind,
        @Schema(description = "Weight in scoring, 0 to 1")
        @DecimalMin("0.0") @DecimalMax("1.0") Float importance,
        Boolean active,
        Boolean alwaysInclude,
        Boolean verbatim,
        Boolean verified,
        @Schema(description = "Replaces the whole list") List<@Size(max = 80) String> skills,
        List<@Size(max = 80) String> metrics,
        List<@Size(max = 120) String> properNouns) {
}
