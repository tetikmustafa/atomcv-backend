package com.mustafatetik.atomcv.profile.domain;

import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** Where an atom came from. Stored in {@code atoms.source} (Bolum 13). */
public enum AtomSource {
    MANUAL,
    CV_UPLOAD,
    GITHUB;

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<AtomSource> {
        public JpaConverter() {
            super(AtomSource.class);
        }
    }
}
