package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * Fields to change on a wording.
 *
 * <p>Separate from {@link VariantDraft} because the two have different rules:
 * a new wording must carry its text — an atom with no words is a fact nobody
 * can read — while a patch may be about anything else. Making one wording the
 * default is the case that matters: it is not a text edit, and requiring the
 * whole sentence back for it means a client has to be holding content it has
 * no other reason to hold (EK D.6.8).
 *
 * <p>Null means "leave it alone". {@code tone} is the one nullable column, so
 * it carries a JsonNullable: undefined leaves the tone, a defined null returns
 * the wording to the neutral register.
 */
public record VariantPatch(
        RichContent content,
        String language,
        JsonNullable<Tone> tone,
        Boolean primary,
        Boolean userEdited) {

    /**
     * <strong>{@code userEdited} may only be cleared, and only on purpose.</strong>
     *
     * <p>Bolum 32.2 protects a wording the person wrote from being replaced by
     * a machine translation. Once the flag is set, something has to be able to
     * take it back — that is the "regenerate the English" button — but nothing
     * may do it as a side effect. Sending it {@code false} is the person
     * saying they are done owning this sentence; sending it {@code true} is
     * refused, because claiming authorship on somebody's behalf is the one
     * direction that could hide a machine translation behind a human's name.
     */
    public VariantPatch {
        if (Boolean.TRUE.equals(userEdited)) {
            throw new IllegalArgumentException(
                    "userEdited is set by writing words, and may only be cleared here");
        }
    }
}
