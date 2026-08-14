package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.Objects;

/** A wording to add to an atom, or the replacement text for one. */
public record VariantDraft(
        RichContent content,
        String language,
        Tone tone,
        Boolean primary) {

    public VariantDraft {
        Objects.requireNonNull(content, "content");
    }
}
