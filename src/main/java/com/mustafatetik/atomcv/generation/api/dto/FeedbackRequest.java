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

        @Schema(description = "1 for good, -1 for bad")
        @NotNull
        Rating rating,

        @Schema(description = "Which part it is about")
        Category category,

        @Schema(description = "Anything else worth saying. Stored, never logged.")
        @Size(max = 4_000)
        String comment,

        @Schema(description = "Allow the CV's content to be read for 48 hours "
                + "so the problem can be diagnosed",
                defaultValue = "false")
        Boolean contentGranted) {

    /**
     * Bolum 13's two values, and the schema says so in the type they are
     * (F-019).
     *
     * <p><strong>An enum rather than a {@code Short} with an
     * {@code allowableValues}.</strong> Swagger's {@code allowableValues} is a
     * {@code String[]} and publishes quoted values whatever the property's
     * type, so the schema said {@code format: int32} and
     * {@code enum: ["1", "-1"]} in the same breath — openapi-typescript
     * believes the enum, and the frontend was carrying an {@code Omit} to undo
     * the string literals while sending the number that was always correct.
     *
     * <p>This is the shape {@link Category} beside it already uses: a closed
     * vocabulary whose wire form is a {@code @JsonValue}. The difference is
     * that this one's wire form is a number, and the compiler now keeps the
     * two values from drifting instead of a hand-written range check.
     */
    @Schema(type = "integer", format = "int32", allowableValues = {"1", "-1"})
    public enum Rating {

        UP((short) 1),
        DOWN((short) -1);

        private final short wire;

        Rating(short wire) {
            this.wire = wire;
        }

        @com.fasterxml.jackson.annotation.JsonValue
        public short wireValue() {
            return wire;
        }

        /**
         * Anything else is refused here rather than by a range check on the
         * record: a rating of zero is neither a thumb up nor a thumb down, and
         * failing at the parse means the endpoint cannot be reached with one.
         */
        @JsonCreator
        public static Rating fromWireValue(short value) {
            return switch (value) {
                case 1 -> UP;
                case -1 -> DOWN;
                default -> throw new IllegalArgumentException(
                        "a rating is 1 or -1");
            };
        }
    }

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

    /** The number Bolum 13's column stores. */
    public short ratingValue() {
        return rating.wireValue();
    }

    public GenerationFeedback.Category domainCategory() {
        return category == null ? null : category.toDomain();
    }

    public boolean granted() {
        return Boolean.TRUE.equals(contentGranted);
    }
}
