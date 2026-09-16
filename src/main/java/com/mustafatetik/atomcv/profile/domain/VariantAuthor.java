package com.mustafatetik.atomcv.profile.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Who produced a variant. Stored in {@code atom_variants.created_by}. The
 * distinction drives design principle 8: work the user wrote is never silently
 * overwritten by work a model wrote.
 *
 * <p><strong>Two values, and V1's comment names four</strong> (denetim,
 * beşinci tur). {@code LLM_EXTRACT} and {@code LLM_REWRITE} were declared,
 * published in the API schema, and unreachable: an import writes {@code USER}
 * deliberately, and a Faz D rewrite is not a variant at all — it lives in
 * {@code generations.rewritten_content}, which is what V11 is about. A closed
 * vocabulary with values nothing can produce is a promise the frontend writes
 * a branch for and never sees taken. V16 narrows the column to match.
 */
public enum VariantAuthor {

    /**
     * Including everything an import wrote.
     *
     * <p>Extraction reads a document the person wrote and keeps their
     * sentences, so what comes out of it is theirs — {@code ProfileWriter}
     * says so at the line that sets this, and it is why {@code LLM_EXTRACT}
     * never had a writer.
     */
    USER,

    /** The one thing a model writes that is kept as a wording of its own. */
    LLM_TRANSLATE;

    /** Lowercase on the wire as well as in the column. */
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
