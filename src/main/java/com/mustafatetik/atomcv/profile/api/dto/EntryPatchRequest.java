package com.mustafatetik.atomcv.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * A partial update where a field can also be <em>cleared</em>.
 *
 * <p>Sections could treat null as "leave alone" because none of their columns
 * are nullable. An entry's are: an end date is absent while a job is ongoing,
 * and a user who typed the wrong organization must be able to empty it again.
 * So the two cases have to be told apart, and JsonNullable is what tells them
 * apart — Java has no tri-state Optional, and Jackson reads an absent Optional
 * field as Optional.empty(), the same as an explicit null:
 *
 * <ul>
 *   <li>field absent from the body → the value is undefined → left alone</li>
 *   <li>field sent as {@code null} → defined, holding null → cleared</li>
 *   <li>field sent with a value → a present value → set</li>
 * </ul>
 *
 * <p>{@code sectionId} is not here. Moving an entry between sections renumbers
 * two lists at once, which is an ordering operation rather than a field edit.
 */
@Schema(name = "EntryPatch")
public record EntryPatchRequest(
        @Size(max = 200) String title,

        // The tri-state is a Java concern; on the wire these are plain nullable
        // fields, and the schema has to say so or a generated client ends up
        // filling in a { present, value } wrapper.
        //
        // `types` rather than `nullable`: this document is OpenAPI 3.1, where
        // null is a type and not a flag. `nullable = true` was silently dropped
        // on the way out, so the schema published `"type": "string"` for a
        // field whose whole purpose is to accept null — a generated client
        // rejected the exact body that clears an end date (EK D.6.4).
        @Schema(implementation = String.class, types = {"string", "null"},
                description = "Send null to clear")
        JsonNullable<@Size(max = 200) String> organization,

        @Schema(implementation = String.class, types = {"string", "null"})
        JsonNullable<@Size(max = 120) String> location,

        @Schema(implementation = LocalDate.class, types = {"string", "null"})
        JsonNullable<LocalDate> startDate,

        @Schema(implementation = LocalDate.class, types = {"string", "null"},
                description = "Send null when the job becomes ongoing again")
        JsonNullable<LocalDate> endDate,

        @Schema(implementation = String.class, types = {"string", "null"})
        JsonNullable<@Size(max = 300) String> url,

        @DecimalMin("0.0") @DecimalMax("1.0") Float importance,
        Boolean active,
        Boolean alwaysInclude,
        Boolean verbatim,
        @Min(0) @Max(50) Short minAtoms) {

    public EntryPatchRequest {
        if (title != null && title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }
}
