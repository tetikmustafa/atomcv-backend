package com.mustafatetik.atomcv.profile.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * How a section is laid out. Stored in {@code sections.layout}, which carries a
 * matching CHECK constraint (Bolum 13).
 */
public enum SectionLayout {
    BULLET_LIST,
    ENTRY_LIST,
    INLINE_LIST,
    TWO_COLUMN;

    /** Lowercase on the wire as well as in the column (EK D.9 · 6). */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static SectionLayout fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<SectionLayout> {
        public JpaConverter() {
            super(SectionLayout.class);
        }
    }
}
