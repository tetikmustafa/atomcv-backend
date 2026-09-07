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
    TWO_COLUMN,

    /**
     * Prose, set straight under the section heading with no bullet in front of
     * it (Bolum 33.4).
     *
     * <p>A summary is the section this exists for. It is one flowing paragraph
     * in every CV that has one, and it was being printed as a bulleted item
     * because {@code BULLET_LIST} is what the column defaults to — a marker in
     * front of a paragraph, which reads as the first of a list that never
     * arrives.
     *
     * <p>Distinct from {@link #INLINE_LIST} rather than folded into it, and the
     * difference is not cosmetic: an inline row is a label and the list it
     * introduces, so its first colon is set in bold. A summary that opened
     * "Backend engineer: five years of..." would have had six words emboldened
     * by a rule that was never about it.
     */
    PARAGRAPH;

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
