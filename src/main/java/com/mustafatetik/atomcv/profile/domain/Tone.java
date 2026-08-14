package com.mustafatetik.atomcv.profile.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Register of a variant. Stored in {@code atom_variants.tone}, nullable: a
 * variant without a tone is the neutral one.
 */
public enum Tone {
    FORMAL,
    CASUAL,
    TECHNICAL;

    /**
     * Lowercase in JSON too: the tone also appears inside
     * {@code profiles.preferences} (Bolum 14.3), which Jackson writes, not the
     * JPA converter below.
     */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Tone fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<Tone> {
        public JpaConverter() {
            super(Tone.class);
        }
    }
}
