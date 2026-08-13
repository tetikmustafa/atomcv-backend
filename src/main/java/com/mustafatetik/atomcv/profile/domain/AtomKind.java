package com.mustafatetik.atomcv.profile.domain;

import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** What a single atom represents. Stored in {@code atoms.kind} (Bolum 13). */
public enum AtomKind {
    BULLET,
    SKILL,
    LANGUAGE,
    CERTIFICATION,
    ABOUT_PARAGRAPH;

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<AtomKind> {
        public JpaConverter() {
            super(AtomKind.class);
        }
    }
}
