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
 * no other reason to hold (EK D.6.4).
 *
 * <p>Null means "leave it alone". {@code tone} is the one nullable column, so
 * it carries a JsonNullable: undefined leaves the tone, a defined null returns
 * the wording to the neutral register.
 */
public record VariantPatch(
        RichContent content,
        String language,
        JsonNullable<Tone> tone,
        Boolean primary) {
}
