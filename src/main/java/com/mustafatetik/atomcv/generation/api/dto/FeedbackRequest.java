package com.mustafatetik.atomcv.generation.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.mustafatetik.atomcv.generation.domain.GenerationFeedback;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * What somebody thought of a generation (Bolum 13, Bolum 48.4).
 *
 * <p>Only the thumb is required. A form that demanded a reason before it
 * accepted the verdict would collect fewer verdicts and worse ones — the
 * category and the comment are for the people who have something to add.
 *
 * @param rating         {@code 1} or {@code -1}, and nothing else
 * @param category       which part was wrong, from Bolum 13's five
 * @param comment        anything they want to say
 * @param contentGranted Bolum 48.4: whether the CV itself may be looked at
 *                       for forty-eight hours to work out what went wrong.
 *                       Sending {@code false} later withdraws a grant that is
 *                       still open
 */
@Schema(description = "A verdict on one generation")
public record FeedbackRequest(

        @Schema(description = "1 for good, -1 for bad", allowableValues = {"1", "-1"})
        @NotNull
        Short rating,

        @Schema(description = "Which part it is about")
        Category category,

        @Schema(description = "Anything else worth saying. Stored, never logged.")
        @Size(max = 4_000)
        String comment,

        @Schema(description = "Allow the CV's content to be read for 48 hours "
                + "so the problem can be diagnosed",
                defaultValue = "false")
        Boolean contentGranted) {

    /** Bolum 13's column vocabulary, lowercase on the wire like every other one. */
    public enum Category {
        SELECTION, WRITING, FORMAT, DENSITY, OTHER;

        @com.fasterxml.jackson.annotation.JsonValue
        public String wireValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        @JsonCreator
        public static Category fromWireValue(String value) {
            return value == null || value.isBlank()
                    ? null
                    : valueOf(value.strip().toUpperCase(Locale.ROOT));
        }

        GenerationFeedback.Category toDomain() {
            return GenerationFeedback.Category.valueOf(name());
        }
    }

    /**
     * <strong>Validated here rather than by an annotation.</strong> Bolum 13's
     * column allows exactly two values and a range would allow zero, which is
     * neither a thumb up nor a thumb down.
     */
    public boolean hasValidRating() {
        return rating != null && (rating == 1 || rating == -1);
    }

    public GenerationFeedback.Category domainCategory() {
        return category == null ? null : category.toDomain();
    }

    public boolean granted() {
        return Boolean.TRUE.equals(contentGranted);
    }
}
