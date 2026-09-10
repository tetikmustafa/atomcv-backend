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
        BudgetBreakdown budget,
        List<UUID> headerOnlyEntries,
        List<RejectedEntry> rejectedEntries) {

    public SelectionState {
        selected = List.copyOf(selected);
        rejected = List.copyOf(rejected);
        // Null rather than empty is what a snapshot written before this field
        // existed deserialises to, and those rows still have to be renderable
        // (EK D.6.3).
        headerOnlyEntries = headerOnlyEntries == null
                ? List.of()
                : List.copyOf(headerOnlyEntries);
        rejectedEntries = rejectedEntries == null
                ? List.of()
                : List.copyOf(rejectedEntries);
    }

    /** A selection with no atomless entry on it, which is most of them. */
    public SelectionState(
            List<SelectedAtom> selected, List<RejectedAtom> rejected, BudgetBreakdown budget) {

        this(selected, rejected, budget, List.of(), List.of());
    }

    /** One with an atomless entry that reached the page, and none that did not. */
    public SelectionState(
            List<SelectedAtom> selected, List<RejectedAtom> rejected, BudgetBreakdown budget,
            List<UUID> headerOnlyEntries) {

        this(selected, rejected, budget, headerOnlyEntries, List.of());
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

    /**
     * An entry that was offered the page by its heading alone and did not get
     * it (Bolum 20.2, 20.5).
     *
     * <p>Its own record rather than a {@link RejectedAtom} with an entry id in
     * it. The two ids are not interchangeable: Bolum 20.5's list is read atom
     * by atom, and one that resolved to nothing would be worse than saying
     * nothing at all — which is what this used to do, and why a degree line
     * could vanish off a full page without a word. The list it belongs in is
     * the one where the id means what it says.
     *
     * <p>{@code BUDGET} is the only reason that reaches it, and the other two
     * cannot: an inactive entry is never offered as a candidate, and the
     * minimum is a statement about bullets that an entry without any is exempt
     * from. A new reason here would want its own thought about which of the
     * two lists it belongs in.
     */
    public record RejectedEntry(UUID entryId, double score, RejectionReason reason) {
    }

    /**
     * Why an atom — or, for {@code BUDGET}, an entry — did not make it. Every
     * one of these is explainable to a user (P7).
     */
    public enum RejectionReason {
        /** There was no room left. */
        BUDGET,

        /** The user switched it off. */
        INACTIVE,

        /**
         * The user took it off <em>this</em> CV (Bolum 24.4).
         *
         * <p>Its own reason and not {@link #INACTIVE}. The two look identical
         * to the algorithm and are opposites to the person: one is a standing
         * decision about the profile, the other is an edit of one document that
         * the next generation will not repeat. A screen offering to undo the
         * second must not offer to undo the first.
         */
        EXCLUDED_BY_DIRECTIVE,

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

    /**
     * Nothing was chosen at all.
     *
     * <p>An entry printed by its heading alone counts: it is a line on the
     * page, so a selection carrying one is not an empty CV even though no atom
     * survived.
     */
    public boolean isEmpty() {
        return selected.isEmpty() && headerOnlyEntries.isEmpty();
    }
}
