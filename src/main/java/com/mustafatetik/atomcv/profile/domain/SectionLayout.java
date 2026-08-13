package com.mustafatetik.atomcv.profile.domain;

import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/**
 * How a section is laid out. Stored in {@code sections.layout}, which carries a
 * matching CHECK constraint (Bolum 13).
 */
public enum SectionLayout {
    BULLET_LIST,
    ENTRY_LIST,
    INLINE_LIST,
    TWO_COLUMN;

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<SectionLayout> {
        public JpaConverter() {
            super(SectionLayout.class);
        }
    }
}
