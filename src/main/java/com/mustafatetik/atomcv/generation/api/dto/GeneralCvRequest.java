package com.mustafatetik.atomcv.generation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * What a general CV request may say (Bolum 14.4).
 *
 * <p>Both fields are optional: left out, the profile's own defaults decide.
 * The body itself is optional too — an empty POST is the ordinary case.
 */
@Schema(description = "Overrides for a general CV. Anything omitted follows the profile.")
public record GeneralCvRequest(

        @Schema(description = "How many pages the CV may take", example = "1",
                minimum = "1", maximum = "10")
        @Min(1) @Max(10)
        Integer maxPages,

        @Schema(description = "Which wording to render, as an ISO 639-1 code",
                example = "en", pattern = "^[a-z]{2}$")
        @Pattern(regexp = "^[a-z]{2}$")
        String language) {

    public static final GeneralCvRequest EMPTY = new GeneralCvRequest(null, null);
}
