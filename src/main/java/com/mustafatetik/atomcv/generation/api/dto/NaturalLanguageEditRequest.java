package com.mustafatetik.atomcv.generation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /generations/{id}/edits} — Bolum 24.2's natural-language edit.
 *
 * @param instruction what should change, in the person's own words
 */
@Schema(description = """
        One sentence about what should change. It is read into a change of \
        which atoms are on the page, and the CV is re-made from its own \
        selection state — so the page limit is re-checked and still holds.

        This one costs a model call and comes off the day's generations. \
        The hand toggle next door does not.""")
public record NaturalLanguageEditRequest(
        @NotBlank
        @Size(max = 500)
        @Schema(example = "take out the Android bullet and put the Kubernetes one back",
                maxLength = 500)
        String instruction) {
}
