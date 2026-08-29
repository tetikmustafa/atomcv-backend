package com.mustafatetik.atomcv.profile.api.dto;

import com.mustafatetik.atomcv.profile.domain.Tone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * A partial update to a wording.
 *
 * <p>Every field is optional, {@code content} included: promoting a wording to
 * the default is not a text edit, and the endpoint used to demand the whole
 * sentence back for it. When content <em>is</em> sent it is the whole of it —
 * a sentence is edited as a sentence, not run by run, and sending the runs
 * wholesale is what lets the server derive the plain text and the hash from
 * one authoritative value.
 *
 * <p>{@code tone} is a JsonNullable for the same reason an entry's dates are:
 * leaving it out has to mean something different from clearing it. Sending it
 * unconditionally is what made a promote wipe the user's tone (EK D.6.8).
 */
@Schema(name = "VariantPatch")
public record VariantPatchRequest(
        @Valid @Schema(description = "The replacement wording. Leave it out to change "
                + "nothing but the fields below.")
        ContentDto content,

        @Size(min = 2, max = 16) @Schema(example = "en") String language,

        // The tri-state is a Java concern; on the wire this is a plain nullable
        // field, and the schema has to say so or a generated client ends up
        // filling in a { present, value } wrapper.
        @Schema(implementation = Tone.class, types = {"string", "null"},
                description = "Send null to return the wording to the neutral register")
        JsonNullable<Tone> tone,

        @Schema(description = "Make this the wording used by default") Boolean primary,

        // AssertFalse rather than a check in the controller: null stays valid
        // under it -- "leave it alone" is most of the traffic -- and the
        // existing MethodArgumentNotValidException handler names the field
        // from the binding result, so the refusal publishes
        // `fields: ["userEdited"]` without a second place to keep the name in
        // step. VariantPatch's constructor still throws: that guard is for a
        // service-layer caller, and this one is for the wire (F-021).
        @AssertFalse
        @Schema(description = "Send `false` to hand a wording back: it stops being "
                + "yours, and a stale one is queued for regeneration (Bolum 32.2's "
                + "\"regenerate\" button). `true` is refused — a wording becomes "
                + "yours by writing words, never by claiming it.")
        Boolean userEdited) {
}
