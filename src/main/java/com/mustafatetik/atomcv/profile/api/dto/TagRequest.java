package com.mustafatetik.atomcv.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A label to put on an atom.
 *
 * <p>The label travels as the person typed it and is canonicalised on the
 * server — trimmed and lowercased with {@code Locale.ROOT} (absolute rule 7).
 * A client that canonicalised it too would be a second implementation of a
 * rule the two halves must agree on exactly, and the response carries the
 * stored form back.
 *
 * <p>The ceiling is the reasoning about unbounded fields: a tag is a word or
 * two, and a column with no limit is an unbounded row, an unbounded render and
 * an unbounded prompt.
 */
@Schema(description = "A label to put on an atom")
public record TagRequest(
        @Schema(description = "As typed; stored trimmed and lowercased",
                example = "data-engineering")
        @NotBlank @Size(max = 64)
        String label) {
}
