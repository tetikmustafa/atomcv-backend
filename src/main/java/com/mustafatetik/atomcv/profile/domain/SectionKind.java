package com.mustafatetik.atomcv.profile.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

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

    /** Lowercase on the wire as well as in the column (EK D.9 · 6). */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static SectionKind fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<SectionKind> {
        public JpaConverter() {
            super(SectionKind.class);
        }
    }
}
