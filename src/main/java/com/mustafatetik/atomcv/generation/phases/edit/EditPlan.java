package com.mustafatetik.atomcv.generation.phases.edit;

import com.mustafatetik.atomcv.generation.selection.GenerationDirectives;
import java.util.List;
import java.util.UUID;

/**
 * The sentence, resolved back to the atoms it named (Bolum 24.2).
 *
 * <p>Its own type rather than a {@link GenerationDirectives} straight out of
 * the phase, because the two are different statements. This is what one
 * sentence asked for; a directive set is everything asked for so far, and the
 * merge of the two belongs to the service that holds the parent row.
 */
public record EditPlan(List<UUID> includeAtoms, List<UUID> excludeAtoms) {

    public EditPlan {
        includeAtoms = List.copyOf(includeAtoms);
        excludeAtoms = List.copyOf(excludeAtoms);
    }

    public GenerationDirectives asDirectives() {
        return new GenerationDirectives(includeAtoms, excludeAtoms);
    }

    /** Counts, never a line of the CV (absolute rule 4). */
    @Override
    public String toString() {
        return "EditPlan[include=" + includeAtoms.size() + ", exclude=" + excludeAtoms.size() + "]";
    }
}
