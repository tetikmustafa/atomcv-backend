package com.mustafatetik.atomcv.generation.selection;

import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.AtomCandidate;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.EntryPlan;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.SectionPlan;
import com.mustafatetik.atomcv.generation.selection.SelectionState.BudgetBreakdown;
import com.mustafatetik.atomcv.generation.selection.SelectionState.RejectedAtom;
import com.mustafatetik.atomcv.generation.selection.SelectionState.RejectionReason;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.shared.error.Resolution;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Faz C: what fits on the page (Bolum 20).
 *
 * <p>This is where the product's promise is kept. It is pure code — the same
 * request produces the same answer, every time — because a page limit that
 * depended on a model's mood would not be a guarantee (design principle 2).
 *
 * <p>Three passes. The first places what the user locked and refuses early if
 * that alone cannot fit. The second fills the rest greedily by value per
 * point, charging an atom for the entry heading it opens. The third looks for
 * swaps the greedy pass could not see.
 */
public final class SelectionPhase {

    /**
     * Bolum 20.3: the fifth bullet from one entry is worth 52% of its score.
     * Without it a strong project can take the whole page, and a CV that says
     * one thing well says nothing else at all.
     */
    private static final double DIVERSITY_DECAY = 0.85;

    /** Below this there is no atom worth trying to fit. */
    private static final double MIN_USEFUL_PT = 1.0;

    /** Bolum 20.3: the swap pass looks at the best twenty that missed out. */
    private static final int SWAP_CANDIDATES = 20;

    private SelectionPhase() {
    }

    public static Result<SelectionState> select(SelectionRequest request) {
        return new Run(request).execute();
    }

    /** One selection, with the working state that goes with it. */
    private static final class Run {

        private final SelectionRequest request;
        private final CapacityModel capacity;
        private final double totalBudgetPt;

        private final Map<UUID, EntryPlan> entries = new LinkedHashMap<>();
        private final Map<UUID, UUID> sectionOfEntry = new HashMap<>();
        private final Map<UUID, UUID> sectionOfAtom = new HashMap<>();

        private final Set<UUID> openSections = new HashSet<>();
        private final Set<UUID> openEntries = new HashSet<>();
        private final Set<UUID> openSectionLists = new HashSet<>();
        private final Map<UUID, Integer> takenFromEntry = new HashMap<>();

        /** What each open entry's heading and list were charged when opened. */
        private final Map<UUID, Double> entryFurniturePt = new HashMap<>();

        private final Map<UUID, SelectedAtom> selected = new LinkedHashMap<>();
        private final List<RejectedAtom> rejected = new ArrayList<>();
        private final Map<UUID, AtomCandidate> pool = new LinkedHashMap<>();

        private double structurePt;
        private double contentPt;

        Run(SelectionRequest request) {
            this.request = request;
            this.capacity = request.capacity();
            this.totalBudgetPt =
                    capacity.pageTextHeightPt() * request.maxPages() * request.budgetFactor();

            for (SectionPlan section : request.sections()) {
                for (AtomCandidate atom : section.atoms()) {
                    sectionOfAtom.put(atom.atomId(), section.sectionId());
                }
                for (EntryPlan entry : section.entries()) {
                    entries.put(entry.entryId(), entry);
                    sectionOfEntry.put(entry.entryId(), section.sectionId());
                    for (AtomCandidate atom : entry.atoms()) {
                        sectionOfAtom.put(atom.atomId(), section.sectionId());
                    }
                }
            }
            // The page's own header is paid before anything is chosen.
            structurePt = capacity.fixedCost(CapacityModel.HEADER_BLOCK);
        }

        Result<SelectionState> execute() {
            partitionByActivity();

            Result<Void> mandatory = placeMandatory();
            if (mandatory.isErr()) {
                return mandatory.map(ignored -> null);
            }

            fillGreedily();
            enforceEntryMinimums();
            improveBySwapping();
            rejectWhatIsLeft();

            return Result.ok(new SelectionState(
                    List.copyOf(selected.values()),
                    List.copyOf(rejected),
                    new BudgetBreakdown(totalBudgetPt, structurePt,
                            totalBudgetPt - structurePt, contentPt)));
        }

        /** An atom the user switched off is not a candidate at all (constraint 3). */
        private void partitionByActivity() {
            for (AtomCandidate atom : allAtoms()) {
                if (atom.active()) {
                    pool.put(atom.atomId(), atom);
                } else {
                    rejected.add(new RejectedAtom(
                            atom.atomId(), atom.score(), RejectionReason.INACTIVE));
                }
            }
        }

        /**
         * Stage 1: what the user pinned, and enough of each pinned entry to
         * reach the minimum worth printing.
         *
         * <p>Bolum 20.3 forces the minimum for every visible entry. That would
         * make a long profile fail rather than drop its weakest entries, so the
         * minimum is forced only where a lock already commits the entry;
         * everywhere else it is enforced after the fact, all or nothing
         * (EK D.8.5).
         */
        private Result<Void> placeMandatory() {
            for (AtomCandidate atom : sortedByScore(pool.values())) {
                if (atom.alwaysInclude()) {
                    include(atom, true);
                }
            }
            for (UUID entryId : List.copyOf(openEntries)) {
                topUpToMinimum(entryId, true);
            }

            if (structurePt + contentPt > totalBudgetPt) {
                return Result.err(conflict());
            }
            return Result.ok(null);
        }

        /**
         * Stage 2: greedy by value per point.
         *
         * <p>Recomputed from scratch each round rather than kept in a priority
         * queue. Including an atom changes what its siblings cost — the entry
         * heading is already paid — and what they are worth, because the fifth
         * bullet of one entry is worth less than the first of another. A queue
         * ordered before those changes is a queue ordering stale numbers.
         */
        private void fillGreedily() {
            while (remainingPt() > MIN_USEFUL_PT) {
                AtomCandidate best = null;
                double bestEfficiency = 0;

                for (AtomCandidate atom : pool.values()) {
                    double cost = effectiveCostOf(atom);
                    if (cost > remainingPt()) {
                        continue;
                    }
                    double efficiency = adjustedScoreOf(atom) / cost;
                    if (best == null || efficiency > bestEfficiency
                            // Determinism: an id decides a tie, never insertion
                            // order or a hash (Bolum 20.3).
                            || (efficiency == bestEfficiency
                                && atom.atomId().toString().compareTo(best.atomId().toString()) < 0)) {
                        best = atom;
                        bestEfficiency = efficiency;
                    }
                }

                if (best == null) {
                    return;
                }
                include(best, false);
            }
        }

        /**
         * Constraint (4): an entry shows its minimum or none of itself. Half an
         * entry reads as a mistake rather than as an edit.
         */
        private void enforceEntryMinimums() {
            for (UUID entryId : List.copyOf(openEntries)) {
                topUpToMinimum(entryId, false);

                EntryPlan entry = entries.get(entryId);
                if (takenFromEntry.getOrDefault(entryId, 0) < entry.minAtoms()) {
                    dropEntry(entryId);
                }
            }
        }

        /**
         * Stage 3: the swap the greedy pass could not see — a strong atom that
         * did not fit, in place of a weaker one that did.
         *
         * <p>One for one, where Bolum 20.3 allows a set. A set swap needs a
         * subset search for a gain that is small at this size, and every extra
         * degree of freedom is another way for two runs to disagree.
         */
        private void improveBySwapping() {
            List<AtomCandidate> wanted = sortedByScore(pool.values()).stream()
                    .limit(SWAP_CANDIDATES)
                    .toList();

            for (AtomCandidate candidate : wanted) {
                double needed = effectiveCostOf(candidate) - remainingPt();
                if (needed <= 0) {
                    continue;
                }
                SelectedAtom weakest = weakestRemovable(needed, candidate.score());
                if (weakest == null) {
                    continue;
                }
                remove(weakest);
                if (effectiveCostOf(candidate) <= remainingPt()) {
                    include(candidate, false);
                } else {
                    // Putting it back beats leaving the page emptier than it
                    // was for a swap that did not happen.
                    include(originalOf(weakest), false);
                }
            }
        }

        private void rejectWhatIsLeft() {
            for (AtomCandidate atom : pool.values()) {
                rejected.add(new RejectedAtom(
                        atom.atomId(), atom.score(), RejectionReason.BUDGET));
            }
            pool.clear();
        }

        // ── the arithmetic ────────────────────────────────────────────────

        private double remainingPt() {
            return totalBudgetPt - structurePt - contentPt;
        }

        /**
         * What an atom costs right now: its own height, plus the furniture it
         * would open. Constraint (5) — the reason this is not a knapsack.
         */
        private double effectiveCostOf(AtomCandidate atom) {
            double cost = atom.renderCostPt();
            UUID sectionId = sectionOfAtom.get(atom.atomId());

            if (sectionId != null && !openSections.contains(sectionId)) {
                cost += capacity.fixedCost(CapacityModel.SECTION_HEADER);
            }
            if (atom.entryId() == null) {
                if (sectionId != null && !openSectionLists.contains(sectionId)) {
                    cost += capacity.fixedCost(CapacityModel.ITEMIZE_OVERHEAD);
                }
            } else if (!openEntries.contains(atom.entryId())) {
                cost += entryHeaderCost(sectionId)
                        + capacity.fixedCost(CapacityModel.ITEMIZE_OVERHEAD);
            }
            return cost;
        }

        /**
         * An entry heading costs more when a list came before it (EK D.8.10).
         *
         * <p>The first entry of a section follows its heading and pays
         * {@code ENTRY_HEADER}; every later one follows the bullets of the
         * entry above it and pays the paragraph skip as well.
         */
        private double entryHeaderCost(UUID sectionId) {
            return capacity.fixedCost(anythingPrintedIn(sectionId)
                    ? CapacityModel.ENTRY_HEADER_AFTER_LIST
                    : CapacityModel.ENTRY_HEADER);
        }

        private boolean anythingPrintedIn(UUID sectionId) {
            if (sectionId == null) {
                return false;
            }
            return openSectionLists.contains(sectionId)
                    || openEntries.stream()
                            .anyMatch(entryId -> sectionId.equals(sectionOfEntry.get(entryId)));
        }

        private double adjustedScoreOf(AtomCandidate atom) {
            int alreadyTaken = atom.entryId() == null
                    ? 0
                    : takenFromEntry.getOrDefault(atom.entryId(), 0);
            return atom.score() * Math.pow(DIVERSITY_DECAY, alreadyTaken);
        }

        private void include(AtomCandidate atom, boolean forcedByLock) {
            UUID sectionId = sectionOfAtom.get(atom.atomId());
            double furniture = effectiveCostOf(atom) - atom.renderCostPt();

            if (atom.entryId() != null && !openEntries.contains(atom.entryId())) {
                entryFurniturePt.put(atom.entryId(), entryHeaderCost(sectionId)
                        + capacity.fixedCost(CapacityModel.ITEMIZE_OVERHEAD));
            }

            if (sectionId != null) {
                openSections.add(sectionId);
                if (atom.entryId() == null) {
                    // The renderer prints a section's own atoms above its
                    // entries, so a list opening here pushes the section's
                    // first entry down into the more expensive position.
                    if (openSectionLists.add(sectionId)) {
                        structurePt += upgradeFirstEntryOf(sectionId);
                    }
                }
            }
            if (atom.entryId() != null) {
                openEntries.add(atom.entryId());
                takenFromEntry.merge(atom.entryId(), 1, Integer::sum);
            }

            structurePt += furniture;
            contentPt += atom.renderCostPt();
            selected.put(atom.atomId(), new SelectedAtom(
                    atom.atomId(), atom.variantId(), atom.score(),
                    atom.renderCostPt(), forcedByLock));
            pool.remove(atom.atomId());
        }

        /**
         * A section list opened above entries that were already charged as if
         * they followed a heading. Only the first of them moves.
         */
        private double upgradeFirstEntryOf(UUID sectionId) {
            double difference = capacity.fixedCost(CapacityModel.ENTRY_HEADER_AFTER_LIST)
                    - capacity.fixedCost(CapacityModel.ENTRY_HEADER);
            for (UUID entryId : openEntries) {
                if (sectionId.equals(sectionOfEntry.get(entryId))
                        && entryFurniturePt.get(entryId) != null
                        && entryFurniturePt.get(entryId)
                                < capacity.fixedCost(CapacityModel.ENTRY_HEADER_AFTER_LIST)
                                        + capacity.fixedCost(CapacityModel.ITEMIZE_OVERHEAD)) {
                    entryFurniturePt.merge(entryId, difference, Double::sum);
                    return difference;
                }
            }
            return 0.0;
        }

        private void remove(SelectedAtom atom) {
            selected.remove(atom.atomId());
            contentPt -= atom.renderCostPt();
            AtomCandidate original = originalOf(atom);
            if (original.entryId() != null) {
                int left = takenFromEntry.merge(original.entryId(), -1, Integer::sum);
                if (left == 0) {
                    openEntries.remove(original.entryId());
                    // Exactly what it was charged, which is not always the
                    // same number (EK D.8.10).
                    structurePt -= entryFurniturePt.remove(original.entryId());
                }
            }
            pool.put(original.atomId(), original);
        }

        private void topUpToMinimum(UUID entryId, boolean forced) {
            EntryPlan entry = entries.get(entryId);
            if (entry == null) {
                return;
            }
            for (AtomCandidate atom : sortedByScore(entry.atoms())) {
                if (takenFromEntry.getOrDefault(entryId, 0) >= entry.minAtoms()) {
                    return;
                }
                if (!pool.containsKey(atom.atomId())) {
                    continue;
                }
                if (forced || effectiveCostOf(atom) <= remainingPt()) {
                    include(atom, forced);
                }
            }
        }

        /** Takes an entry off the page whole, and gives its space back. */
        private void dropEntry(UUID entryId) {
            for (AtomCandidate atom : entries.get(entryId).atoms()) {
                SelectedAtom chosen = selected.get(atom.atomId());
                if (chosen == null) {
                    continue;
                }
                if (chosen.forcedByLock()) {
                    // A locked atom keeps its entry alive whatever the minimum
                    // says: the user asked for it by name.
                    return;
                }
            }
            for (AtomCandidate atom : entries.get(entryId).atoms()) {
                if (selected.containsKey(atom.atomId())) {
                    remove(selected.get(atom.atomId()));
                    pool.remove(atom.atomId());
                    rejected.add(new RejectedAtom(atom.atomId(), atom.score(),
                            RejectionReason.ENTRY_BELOW_MINIMUM));
                }
            }
            openEntries.remove(entryId);
        }

        private SelectedAtom weakestRemovable(double neededPt, double betterThan) {
            SelectedAtom weakest = null;
            for (SelectedAtom chosen : selected.values()) {
                if (chosen.forcedByLock() || chosen.score() >= betterThan) {
                    continue;
                }
                if (chosen.renderCostPt() < neededPt) {
                    continue;
                }
                if (weakest == null || chosen.score() < weakest.score()
                        || (chosen.score() == weakest.score()
                            && chosen.atomId().toString().compareTo(weakest.atomId().toString()) < 0)) {
                    weakest = chosen;
                }
            }
            return weakest;
        }

        private AtomCandidate originalOf(SelectedAtom atom) {
            for (AtomCandidate candidate : allAtoms()) {
                if (candidate.atomId().equals(atom.atomId())) {
                    return candidate;
                }
            }
            throw new IllegalStateException("A selected atom that was never a candidate");
        }

        private PipelineError conflict() {
            double pinnedPt = structurePt + contentPt;
            int fitting = 0;
            double running = capacity.fixedCost(CapacityModel.HEADER_BLOCK);
            for (SelectedAtom atom : selected.values()) {
                running += atom.renderCostPt();
                if (running <= totalBudgetPt) {
                    fitting++;
                }
            }
            return new PipelineError.ConflictingPreferences(pinnedPt, totalBudgetPt, List.of(
                    Resolution.of(ResolutionAction.INCREASE_PAGE_LIMIT,
                            "maxPages", request.maxPages() + 1),
                    Resolution.of(ResolutionAction.REVIEW_PINS),
                    Resolution.of(ResolutionAction.KEEP_TOP_PINNED, "keep", fitting)));
        }

        private List<AtomCandidate> allAtoms() {
            List<AtomCandidate> all = new ArrayList<>();
            for (SectionPlan section : request.sections()) {
                all.addAll(section.atoms());
                for (EntryPlan entry : section.entries()) {
                    all.addAll(entry.atoms());
                }
            }
            return all;
        }

        private static List<AtomCandidate> sortedByScore(Iterable<AtomCandidate> atoms) {
            List<AtomCandidate> sorted = new ArrayList<>();
            atoms.forEach(sorted::add);
            sorted.sort(Comparator.comparingDouble(AtomCandidate::score).reversed()
                    .thenComparing(atom -> atom.atomId().toString()));
            return sorted;
        }
    }
}
