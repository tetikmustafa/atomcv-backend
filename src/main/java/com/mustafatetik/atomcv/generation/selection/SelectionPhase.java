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
import java.util.LinkedHashSet;
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

        // Ordered, and not for tidiness: upgradeFirstEntryOf walks this and
        // charges the entry it reaches first, so a salted iteration order
        // moves points between entries and changes what a removal refunds.
        private final Set<UUID> openEntries = new LinkedHashSet<>();
        private final Set<UUID> openSectionLists = new HashSet<>();
        private final Map<UUID, Integer> takenFromEntry = new HashMap<>();

        /**
         * Entries on the page with no bullets under them. Ordered, because it
         * leaves here for a JSONB column and a response.
         */
        private final Set<UUID> headerOnly = new LinkedHashSet<>();

        /** What each open entry's heading and list were charged when opened. */
        private final Map<UUID, Double> entryFurniturePt = new HashMap<>();

        /** What each open section's heading was charged, for the refund when it empties. */
        private final Map<UUID, Double> sectionHeaderPt = new HashMap<>();

        /** What each open section's own list was charged, on the same terms. */
        private final Map<UUID, Double> sectionListPt = new HashMap<>();

        /** Section-level atoms — the ones under no entry — currently on the page. */
        private final Map<UUID, Integer> takenFromSection = new HashMap<>();

        /** Which entry a section's list pushed down, so that closing it can put it back. */
        private final Map<UUID, UUID> upgradedByList = new HashMap<>();

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

            fillUntilStable();
            improveBySwapping();
            // A swap that falls through hands back more than it takes, and one
            // that lands can open an entry short of its minimum. Either way the
            // page is not finished until filling and the minimum agree.
            fillUntilStable();
            rejectWhatIsLeft();

            // The heading candidates leave by their own door. They travel
            // through the algorithm as candidates because that is what makes
            // them compete for the page on the same terms, but they are not
            // atoms, and a caller that read one out of `selected` would look
            // up an atom id that belongs to an entry.
            List<SelectedAtom> atoms = selected.values().stream()
                    .filter(atom -> !headerOnly.contains(atom.atomId()))
                    .toList();

            return Result.ok(new SelectionState(
                    atoms,
                    List.copyOf(rejected),
                    new BudgetBreakdown(totalBudgetPt, structurePt,
                            totalBudgetPt - structurePt, contentPt),
                    List.copyOf(headerOnly)));
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
                            // Determinism: the wording decides a tie, never
                            // insertion order (Bolum 20.3). The id is only the
                            // last resort, and it is not stable across imports.
                            || (efficiency == bestEfficiency
                                && atom.tieBreak().compareTo(best.tieBreak()) < 0)) {
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
         * Fill, and keep filling for as long as an entry leaves the page.
         *
         * <p>{@link #enforceEntryMinimums()} refunds everything a dropped entry
         * was charged, and until this loop existed nothing ever offered that
         * space to anyone else — the greedy pass had already run and does not
         * come back on its own. A measured run finished with 133 pt of a 352 pt
         * free budget unclaimed while ten atoms sat in the pool marked
         * {@code BUDGET}: a reason that was true when it was written and false
         * by the time the run ended.
         *
         * <p>Terminates. A drop takes its entry off the page and its atoms out
         * of the pool for good, so every further round has one fewer entry left
         * to drop.
         */
        private void fillUntilStable() {
            do {
                fillGreedily();
            } while (enforceEntryMinimums());
        }

        /**
         * Constraint (4): an entry shows its minimum or none of itself. Half an
         * entry reads as a mistake rather than as an edit.
         *
         * @return whether an entry left the page, which is the only outcome
         *         here that frees budget somebody else could use
         */
        private boolean enforceEntryMinimums() {
            boolean dropped = false;
            for (UUID entryId : List.copyOf(openEntries)) {
                if (headerOnly.contains(entryId)) {
                    // The minimum is a statement about bullets, and this entry
                    // has none to reach it with. Applying it here would delete
                    // exactly the line this exists to print.
                    continue;
                }
                topUpToMinimum(entryId, false);

                EntryPlan entry = entries.get(entryId);
                if (takenFromEntry.getOrDefault(entryId, 0) < entry.minAtoms()) {
                    dropped |= dropEntry(entryId);
                }
            }
            return dropped;
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

        /**
         * Everything still in the pool was offered the page and did not fit.
         *
         * <p>{@code BUDGET} is only honest because {@link #fillUntilStable()}
         * ran last: an atom that would have fitted has already been taken, and
         * one whose entry went is already rejected with its own reason. Move
         * this above the filling and the label goes back to being a guess.
         */
        private void rejectWhatIsLeft() {
            for (AtomCandidate atom : pool.values()) {
                if (atom.headerOnly()) {
                    // No rejection for a heading that did not fit: every
                    // RejectedAtom names an atom, and this one would name an
                    // entry. Bolum 20.5's list is what the user is shown
                    // atom by atom, and an id in it that resolves to nothing
                    // is worse than the silence.
                    continue;
                }
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
         *
         * <p>A heading candidate is the same sum with both halves empty: it has
         * no height of its own, and it opens no list, because there are no
         * bullets to put in one.
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
                cost += entryFurnitureCost(atom, sectionId);
            }
            return cost;
        }

        /**
         * What opening this atom's entry costs: the heading, and the list the
         * atom is the first bullet of. A heading candidate pays the first and
         * not the second (Bolum 20.2, constraint 5).
         */
        private double entryFurnitureCost(AtomCandidate atom, UUID sectionId) {
            double furniture = entryHeaderCost(sectionId);
            if (!atom.headerOnly()) {
                furniture += capacity.fixedCost(CapacityModel.ITEMIZE_OVERHEAD);
            }
            return furniture;
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
                entryFurniturePt.put(atom.entryId(), entryFurnitureCost(atom, sectionId));
                if (atom.headerOnly()) {
                    headerOnly.add(atom.entryId());
                }
            }

            if (sectionId != null) {
                if (openSections.add(sectionId)) {
                    // Recorded, not just flagged: it has to be given back by
                    // the same amount when the last thing under it leaves.
                    sectionHeaderPt.put(sectionId,
                            capacity.fixedCost(CapacityModel.SECTION_HEADER));
                }
                if (atom.entryId() == null) {
                    takenFromSection.merge(sectionId, 1, Integer::sum);
                    // The renderer prints a section's own atoms above its
                    // entries, so a list opening here pushes the section's
                    // first entry down into the more expensive position.
                    if (openSectionLists.add(sectionId)) {
                        sectionListPt.put(sectionId,
                                capacity.fixedCost(CapacityModel.ITEMIZE_OVERHEAD));
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
                Double charged = entryFurniturePt.get(entryId);
                if (sectionId.equals(sectionOfEntry.get(entryId))
                        && charged != null
                        // Still at the cheaper heading, so it has not been
                        // moved yet. The ceiling differs by kind: an entry
                        // opened by its heading alone never paid for a list.
                        && charged < ceilingFor(entryId)) {
                    entryFurniturePt.merge(entryId, difference, Double::sum);
                    upgradedByList.put(sectionId, entryId);
                    return difference;
                }
            }
            return 0.0;
        }

        /** What this entry's furniture comes to once it sits after a list. */
        private double ceilingFor(UUID entryId) {
            double ceiling = capacity.fixedCost(CapacityModel.ENTRY_HEADER_AFTER_LIST);
            if (!headerOnly.contains(entryId)) {
                ceiling += capacity.fixedCost(CapacityModel.ITEMIZE_OVERHEAD);
            }
            return ceiling;
        }

        private void remove(SelectedAtom atom) {
            selected.remove(atom.atomId());
            contentPt -= atom.renderCostPt();
            AtomCandidate original = originalOf(atom);
            UUID sectionId = sectionOfAtom.get(original.atomId());

            if (original.entryId() != null) {
                int left = takenFromEntry.merge(original.entryId(), -1, Integer::sum);
                if (left == 0) {
                    openEntries.remove(original.entryId());
                    // Exactly what it was charged, which is not always the
                    // same number (EK D.8.10).
                    structurePt -= entryFurniturePt.remove(original.entryId());
                    headerOnly.remove(original.entryId());
                }
            } else if (sectionId != null) {
                int left = takenFromSection.merge(sectionId, -1, Integer::sum);
                if (left == 0 && openSectionLists.remove(sectionId)) {
                    structurePt -= sectionListPt.remove(sectionId);
                    structurePt -= downgradeFirstEntryOf(sectionId);
                }
            }
            closeSectionIfEmpty(sectionId);
            pool.put(original.atomId(), original);
        }

        /**
         * A section heading is printed for the content under it, so it is
         * charged when the first atom arrives and has to be handed back when
         * the last one leaves.
         *
         * <p>Until this existed a dropped entry refunded its own furniture and
         * left the heading behind. A measured run paid for five section
         * headings and printed four, and those twenty-four points went missing
         * from a page that was already under-filled — invisibly, because the
         * budget still balanced against a structure figure that was wrong.
         */
        private void closeSectionIfEmpty(UUID sectionId) {
            if (sectionId == null || !openSections.contains(sectionId)
                    || openSectionLists.contains(sectionId)) {
                return;
            }
            boolean stillHasEntry = openEntries.stream()
                    .anyMatch(entryId -> sectionId.equals(sectionOfEntry.get(entryId)));
            if (stillHasEntry) {
                return;
            }
            openSections.remove(sectionId);
            structurePt -= sectionHeaderPt.remove(sectionId);
        }

        /**
         * Puts back what {@link #upgradeFirstEntryOf} moved, once the list that
         * pushed it down has closed.
         *
         * <p>Approximate in the same direction the upgrade is: it moves the one
         * entry that was charged, and if that entry has already left the page
         * it refunds nothing, because the removal refunded the upgraded figure
         * whole.
         */
        private double downgradeFirstEntryOf(UUID sectionId) {
            UUID entryId = upgradedByList.remove(sectionId);
            if (entryId == null || !entryFurniturePt.containsKey(entryId)) {
                return 0.0;
            }
            double difference = capacity.fixedCost(CapacityModel.ENTRY_HEADER_AFTER_LIST)
                    - capacity.fixedCost(CapacityModel.ENTRY_HEADER);
            entryFurniturePt.merge(entryId, -difference, Double::sum);
            return difference;
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

        /**
         * Takes an entry off the page whole, and gives its space back.
         *
         * @return whether it went. A locked entry stays, and the caller has to
         *         know that nothing was freed or it would loop forever waiting
         *         for a page that never changes.
         */
        private boolean dropEntry(UUID entryId) {
            for (AtomCandidate atom : entries.get(entryId).atoms()) {
                SelectedAtom chosen = selected.get(atom.atomId());
                if (chosen == null) {
                    continue;
                }
                if (chosen.forcedByLock()) {
                    // A locked atom keeps its entry alive whatever the minimum
                    // says: the user asked for it by name.
                    return false;
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
            closeSectionIfEmpty(sectionOfEntry.get(entryId));
            return true;
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
                    // The wording decides a tie, not the id: ids are minted
                    // fresh on every import and the same CV would otherwise
                    // order two equal atoms differently each time (Bolum 20.3).
                    .thenComparing(AtomCandidate::tieBreak)
                    .thenComparing(atom -> atom.atomId().toString()));
            return sorted;
        }
    }
}
