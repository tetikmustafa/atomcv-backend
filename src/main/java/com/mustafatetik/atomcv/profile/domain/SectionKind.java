package com.mustafatetik.atomcv.profile.domain;

import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** What a section holds. Stored in {@code sections.kind} (Bolum 13). */
public enum SectionKind {
    ABOUT,
    EDUCATION,
    EXPERIENCE,
    PROJECTS,
    SKILLS,
    SOFT_SKILLS,
    LANGUAGES,
    CUSTOM;

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<SectionKind> {
        public JpaConverter() {
            super(SectionKind.class);
        }
    }
}
