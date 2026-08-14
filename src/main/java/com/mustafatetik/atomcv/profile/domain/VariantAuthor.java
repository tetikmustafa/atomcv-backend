package com.mustafatetik.atomcv.profile.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Who produced a variant. Stored in {@code atom_variants.created_by}
 * (Bolum 13). The distinction drives design principle 8: work the user wrote
 * is never silently overwritten by work a model wrote.
 */
public enum VariantAuthor {
    USER,
    LLM_EXTRACT,
    LLM_TRANSLATE,
    LLM_REWRITE;

    /** Lowercase on the wire as well as in the column (EK D.9 · 6). */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static VariantAuthor fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<VariantAuthor> {
        public JpaConverter() {
            super(VariantAuthor.class);
        }
    }
}
