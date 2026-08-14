package com.mustafatetik.atomcv.profile.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** What a single atom represents. Stored in {@code atoms.kind} (Bolum 13). */
public enum AtomKind {
    BULLET,
    SKILL,
    LANGUAGE,
    CERTIFICATION,
    ABOUT_PARAGRAPH;

    /** Lowercase on the wire as well as in the column (EK D.9 · 6). */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static AtomKind fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<AtomKind> {
        public JpaConverter() {
            super(AtomKind.class);
        }
    }
}
