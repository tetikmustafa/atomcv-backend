package com.mustafatetik.atomcv.profile.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** Where an atom came from. Stored in {@code atoms.source} (Bolum 13). */
public enum AtomSource {
    MANUAL,
    CV_UPLOAD,
    GITHUB;

    /** Lowercase on the wire as well as in the column (EK D.9 · 6). */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static AtomSource fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<AtomSource> {
        public JpaConverter() {
            super(AtomSource.class);
        }
    }
}
