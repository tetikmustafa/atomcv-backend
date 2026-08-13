package com.mustafatetik.atomcv.profile.domain;

import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/**
 * Register of a variant. Stored in {@code atom_variants.tone}, nullable: a
 * variant without a tone is the neutral one.
 */
public enum Tone {
    FORMAL,
    CASUAL,
    TECHNICAL;

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<Tone> {
        public JpaConverter() {
            super(Tone.class);
        }
    }
}
