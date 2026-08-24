package com.mustafatetik.atomcv.profile.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Who attached a tag to an atom. Stored in {@code atom_tags.source}.
 *
 * <p>The column carries a {@code CHECK} constraint over exactly these two
 * values, which is why this is an enum and not a string: Bolum 13 puts the
 * constraint in the schema, and a third value would be refused by Postgres at
 * insert time rather than by the type system at compile time.
 */
public enum TagSource {

    /** Derived from the atom's own content. */
    AUTO,

    /** The user typed it, and nothing may remove it on their behalf. */
    USER;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static TagSource fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<TagSource> {
        public JpaConverter() {
            super(TagSource.class);
        }
    }
}
