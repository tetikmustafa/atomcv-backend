package com.mustafatetik.atomcv.profile.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * How a section is laid out. Stored in {@code sections.layout}, which carries
 * a matching CHECK constraint.
 *
 * <p><strong>{@code TWO_COLUMN} was a fifth value and it never reached a
 * page.</strong> It was accepted by {@code PATCH /profile/sections}, allowed by
 * the constraint, published in the schema — and {@code LatexDocumentRenderer}
 * fell through to the entry list on purpose, because all three templates are
 * single-column and 33.5 records the reason as ATS extraction. So the one value
 * here a person could choose was the one the document ignored, and nothing told
 * them. The other dead vocabulary entries V17 removed were outputs, which cost
 * the frontend a branch; this one cost a user a choice they thought they had
 * made (the sixth audit). It comes back if a two-column template ever does, and
 * that is a template decision rather than a column one.
 */
public enum SectionLayout {
    BULLET_LIST,
    ENTRY_LIST,
    INLINE_LIST,

    /**
     * Prose, set straight under the section heading with no bullet in front of
     * it.
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

    /** Lowercase on the wire as well as in the column. */
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
