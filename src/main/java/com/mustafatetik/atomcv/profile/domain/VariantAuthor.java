package com.mustafatetik.atomcv.profile.domain;

import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;

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

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<VariantAuthor> {
        public JpaConverter() {
            super(VariantAuthor.class);
        }
    }
}
