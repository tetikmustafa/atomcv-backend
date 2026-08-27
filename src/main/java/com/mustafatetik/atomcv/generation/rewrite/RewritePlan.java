package com.mustafatetik.atomcv.generation.rewrite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What Faz D is going to do, decided before a single call is made
 * (Bolum 21.1-21.3).
 *
 * <p>Two halves, and the first one costs nothing. {@code wordings} is the
 * answer to Bolum 21.1 — which of the wordings the person already wrote should
 * be printed — and it covers every selected atom, including the ones no model
 * will ever see. {@code candidates} is the short list Bolum 21.2 admits.
 *
 * <p>A plan rather than a loop because the decisions are worth looking at on
 * their own: the cap, the floor and the ceiling are each a promise to the
 * user, and a promise buried inside a rewrite loop is one nothing can assert
 * against.
 *
 * @param wordings   every selected atom, mapped to the variant that should be
 *                   printed for it
 * @param candidates the atoms worth rewriting, best first
 */
public record RewritePlan(Map<UUID, UUID> wordings, List<RewriteCandidate> candidates) {

    public RewritePlan {
        wordings = Map.copyOf(wordings);
        candidates = List.copyOf(candidates);
    }

    public static RewritePlan nothingToDo(Map<UUID, UUID> wordings) {
        return new RewritePlan(wordings, List.of());
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
        return "wordings=" + wordings.size()
                + " candidates=" + candidates.size()
                + " adapt=" + byIntent.getOrDefault(RewriteIntent.ADAPT, 0)
                + " compress=" + byIntent.getOrDefault(RewriteIntent.COMPRESS, 0);
    }
}
