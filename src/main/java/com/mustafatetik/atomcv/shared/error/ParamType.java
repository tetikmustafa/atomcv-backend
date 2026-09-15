package com.mustafatetik.atomcv.shared.error;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The JSON type of one error parameter.
 *
 * <p>The frontend writes an ICU message per error code, and an ICU placeholder
 * cannot be written without knowing whether the value is a number or a string:
 * {@code {pinnedPages, number}} formats, {@code {pinnedPages}} interpolates.
 * So the type is part of the contract, not an implementation detail.
 */
public enum ParamType {

    STRING("string", String.class),
    INTEGER("integer", Integer.class, Long.class, Short.class),
    NUMBER("number", Integer.class, Long.class, Short.class, Double.class, Float.class),
    BOOLEAN("boolean", Boolean.class),
    TIMESTAMP("timestamp", Instant.class),
    UUID_VALUE("uuid", UUID.class),
    STRING_ARRAY("string[]", List.class);

    private final String wireName;
    private final List<Class<?>> accepted;

    ParamType(String wireName, Class<?>... accepted) {
        this.wireName = wireName;
        this.accepted = List.of(accepted);
    }

    /**
     * How the published catalogue spells this type.
     *
     * <p>Not {@link #name()}: the catalogue is read by somebody writing an ICU
     * message, so it says {@code string[]} and {@code uuid} rather than
     * {@code STRING_ARRAY} and {@code UUID_VALUE}. The spelling lived only in
     * the test that checked the table until the table began to be generated
     * from this enum — at which point a second copy would have been a second
     * answer to what a type is called.
     */
    public String wireName() {
        return wireName;
    }

    /**
     * Whether a value may be published under this type. A whole number is
     * accepted as {@code NUMBER} too — 1 and 1.0 are the same page count, and
     * refusing the first would make the catalogue harder to satisfy than the
     * contract it describes.
     */
    public boolean accepts(Object value) {
        if (value == null) {
            return false;
        }
        if (this == STRING_ARRAY) {
            return value instanceof List<?> list && list.stream().allMatch(String.class::isInstance);
        }
        return accepted.stream().anyMatch(type -> type.isInstance(value));
    }
}
