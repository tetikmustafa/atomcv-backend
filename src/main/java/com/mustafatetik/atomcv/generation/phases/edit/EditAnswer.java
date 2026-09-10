package com.mustafatetik.atomcv.generation.phases.edit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * What the model answers with (Bolum 24.2, {@code selection_edit/schema.json}).
 *
 * <p>Numbers, never ids and never text. The schema says so and this says so
 * again: a record that could hold a sentence would eventually be given one.
 *
 * @param keep       numbers from the held-back list to put on the page
 * @param drop       numbers from the page to take off
 * @param understood whether the sentence named any line at all. False is an
 *                   ordinary answer -- "make it shorter" names no line -- and
 *                   the prompt asks for it in preference to a guess
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EditAnswer(List<Integer> keep, List<Integer> drop, boolean understood) {

    public EditAnswer {
        keep = keep == null ? List.of() : List.copyOf(keep);
        drop = drop == null ? List.of() : List.copyOf(drop);
    }
}
