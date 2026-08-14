package com.mustafatetik.atomcv.profile.api.dto;

import com.mustafatetik.atomcv.profile.domain.Tone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A wording to add to an atom, or a replacement for one.
 *
 * <p>On a patch, content is the whole of it: a sentence is edited as a
 * sentence, not run by run. Sending the runs back wholesale is also what lets
 * the server derive the plain text and the hash from one authoritative value.
 */
@Schema(name = "VariantWrite")
public record VariantRequest(
        @NotNull @Valid ContentDto content,
        @Size(min = 2, max = 16) @Schema(example = "en") String language,
        @Schema(description = "Leave out for the neutral register") Tone tone,
        @Schema(description = "Make this the wording used by default") Boolean primary) {
}
