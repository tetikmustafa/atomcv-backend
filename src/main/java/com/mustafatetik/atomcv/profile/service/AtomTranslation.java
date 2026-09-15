package com.mustafatetik.atomcv.profile.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One line of a CV in another language.
 *
 * @param text     the translated sentence
 * @param emphasis substrings of {@code text} worth marking, quoted exactly —
 *                 the same first-match rule normalisation uses turns them into
 *                 runs, so a paraphrase produces no bold at all
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AtomTranslation(String text, List<String> emphasis) {

    public AtomTranslation {
        text = text == null ? "" : text;
        emphasis = emphasis == null ? List.of() : List.copyOf(emphasis);
    }
}
