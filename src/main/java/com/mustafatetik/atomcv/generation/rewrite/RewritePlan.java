package com.mustafatetik.atomcv.generation.rewrite;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * What Faz D is going to do, decided before a single call is made
 * (Bolum 21.2-21.3).
 *
 * <p>A plan rather than a loop because the decisions are worth looking at on
 * their own: the cap, the floor and the ceiling are each a promise to the
 * user, and a promise buried inside a rewrite loop is one nothing can assert
 * against.
 *
 * <p>Only the short list. Every other selected atom is printed exactly as the
 * person wrote it, which needs no entry here — the renderer already knows
 * which wording selection costed.
 *
 * @param candidates the atoms worth rewriting, best first
 */
public record RewritePlan(List<RewriteCandidate> candidates) {

    private static final RewritePlan NOTHING_TO_DO = new RewritePlan(List.of());

    public RewritePlan {
        candidates = List.copyOf(candidates);
    }

    public static RewritePlan nothingToDo() {
        return NOTHING_TO_DO;
    }

    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    /**
     * Counts and thresholds, never a line of the CV (absolute rule 4).
     */
    public String shape() {
        var byIntent = new LinkedHashMap<RewriteIntent, Integer>();
        for (RewriteCandidate candidate : candidates) {
            byIntent.merge(candidate.intent(), 1, Integer::sum);
        }
        return "candidates=" + candidates.size()
                + " adapt=" + byIntent.getOrDefault(RewriteIntent.ADAPT, 0)
                + " compress=" + byIntent.getOrDefault(RewriteIntent.COMPRESS, 0);
    }
}
