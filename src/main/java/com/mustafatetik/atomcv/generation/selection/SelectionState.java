package com.mustafatetik.atomcv.generation.selection;

import java.util.List;
import java.util.UUID;

/**
 * What was chosen, what was not, and what it all costs (Bolum 20.5, 14.5).
 *
 * <p>Stored with the generation, because it is the only record of why the CV
 * looks the way it does — and because an edit later applies to this, never to
 * the rendered output (design principle 6).
 */
public record SelectionState(
        List<SelectedAtom> selected,
        List<RejectedAtom> rejected,
        BudgetBreakdown budget) {

    public SelectionState {
        selected = List.copyOf(selected);
        rejected = List.copyOf(rejected);
    }

    public record SelectedAtom(
            UUID atomId,
            UUID variantId,
            double score,
            double renderCostPt,
            boolean forcedByLock) {
    }

    public record RejectedAtom(UUID atomId, double score, RejectionReason reason) {
    }

    /** Why an atom did not make it. Every one of these is explainable to a user (P7). */
    public enum RejectionReason {
        /** There was no room left. */
        BUDGET,

        /** The user switched it off. */
        INACTIVE,

        /** Its entry could not reach the minimum worth printing, so the entry went whole. */
        ENTRY_BELOW_MINIMUM
    }

    /**
     * Where the page went (Bolum 26.3).
     *
     * @param totalPt     the page limit in points
     * @param fixedPt     what the furniture costs: headings, entry headers, lists
     * @param freePt      what was left for content
     * @param usedPt      what the selected content occupies
     */
    public record BudgetBreakdown(double totalPt, double fixedPt, double freePt, double usedPt) {

        public double remainingPt() {
            return freePt - usedPt;
        }
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }
}
