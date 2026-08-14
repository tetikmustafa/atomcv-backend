package com.mustafatetik.atomcv.shared.error;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The JSON type of one error parameter.
 *
 * <p>The frontend writes an ICU message per error code, and an ICU placeholder
 * cannot be written without knowing whether the value is a number or a string:
 * {@code {pinnedPages, number}} formats, {@code {pinnedPages}} interpolates. So
 * the type is part of the contract, not an implementation detail (EK D.6).
 */
public enum ParamType {

    STRING(String.class),
    INTEGER(Integer.class, Long.class, Short.class),
    NUMBER(Integer.class, Long.class, Short.class, Double.class, Float.class),
    BOOLEAN(Boolean.class),
    TIMESTAMP(Instant.class),
    UUID_VALUE(UUID.class),
    STRING_ARRAY(List.class);

    private final List<Class<?>> accepted;

    ParamType(Class<?>... accepted) {
        this.accepted = List.of(accepted);
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
