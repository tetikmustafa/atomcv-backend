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

        /**
         * Atoms placed to meet a {@link SectionFloor}, which the swap pass may
         * not trade away.
         *
         * <p>Separate from {@code forcedByLock} on purpose, and it cost a
         * failing test to learn why they are not the same thing. A lock is the
         * user naming an atom; this is the document keeping its shape. They
         * reach the record differently too — {@code pinnedCostPt} answers "what
         * did the user's own choices cost", and counting a floor there would
         * report a choice nobody made.
         */
        private final Set<UUID> reservedByFloor = new LinkedHashSet<>();

        /** Each section's plan, so an atom can be traced back to its policy. */
        private final Map<UUID, SectionPlan> planOfSection = new LinkedHashMap<>();

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
                planOfSection.put(section.sectionId(), section);
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

            placeSectionFloors();
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
         * Stage 1b: what each section is worth printing at, before anything
         * competes for the rest ({@link SectionFloor}).
         *
         * <p><strong>The stage the page was missing.</strong> Without it the
         * greedy pass ranks every atom against every other, and a measured run
         * put twenty atoms on the page with all twenty from Projects: no
         * experience, no skills, no summary. Every one of those choices was the
         * best answer to "which atom is worth the most per point", and the
         * document was not a CV.
         *
         * <p>Reserved in two passes, and the order matters. The first gives
         * every section its {@link SectionFloor#hardFloor()} — so a section
         * cannot be squeezed out by the one above it, which is what a single
         * pass in priority order would do to Languages every time. The second
         * tops each up to its full floor, in priority order, so what is scarce
         * goes to the sections a reader looks for first.
         *
         * <p>Nothing here is forced, and the two passes are the whole of how a
         * floor degrades. A floor is a ceiling on what may be reserved and
         * never a demand: a profile with one job reserves one, and a profile
         * with no projects prints no Projects heading. Where the page runs out
         * the second pass simply stops taking, so a section keeps the hard
         * floor the first pass gave it — thinner, and still there. Dropping a
         * section is not one of the outcomes: a CV missing its experience is
         * not a smaller CV, it is a different document.
         */
        private void placeSectionFloors() {
            List<SectionPlan> byPriority = new ArrayList<>(request.sections());
            byPriority.sort(Comparator.comparingInt(SectionPlan::priority));

            Map<UUID, SectionFloor> floors = new LinkedHashMap<>();
            for (SectionPlan section : byPriority) {
                if (!section.floor().isNone()) {
                    floors.put(section.sectionId(), section.floor());
                }
            }
            if (floors.isEmpty()) {
                return;
            }

            for (SectionPlan section : byPriority) {
                SectionFloor floor = floors.get(section.sectionId());
                if (floor != null) {
                    reserve(section, floor.hardFloor());
                }
            }
            for (SectionPlan section : byPriority) {
                SectionFloor floor = floors.get(section.sectionId());
                if (floor != null) {
                    reserve(section, floor);
                }
            }
        }

        /**
         * As much of one section's floor as the profile has and the page can
         * hold, best first.
         *
         * <p>Entries are chosen by their own best atom rather than by an
         * average: a section reserving two roles wants the two the posting
         * cares about, and an average buries a strong role behind a long one.
         */
        private void reserve(SectionPlan section, SectionFloor floor) {
            // The section's own atoms first, because that is where the renderer
            // prints them and because they are the cheapest way to reach the
            // floor: no entry heading, no list of their own.
            for (AtomCandidate atom : sortedByScore(section.atoms())) {
                if (takenFromSection.getOrDefault(section.sectionId(), 0) >= floor.atoms()) {
                    break;
                }
                takeIfItFits(atom);
            }
            if (floor.entries() == 0) {
                return;
            }

            List<EntryPlan> ranked = new ArrayList<>(section.entries());
            ranked.sort(Comparator.comparingDouble(
                    (EntryPlan entry) -> bestScoreIn(entry)).reversed()
                    // The wording decides a tie, not the id — the same rule
                    // sortedByScore follows, and for the same reason. Ids are
                    // minted fresh on every import, and a profile whose entries
                    // score alike is not a corner case: fourteen projects with
                    // no dates all score the same in general mode, so the two
                    // this floor reserves were being chosen by a random UUID.
                    // Reading the same CV twice then produced two different
                    // pages, which is Principle 2 broken (Bolum 20.3).
                    .thenComparing(Run::wordingOf)
                    .thenComparing(entry -> entry.entryId().toString()));

            int openedHere = 0;
            for (EntryPlan entry : ranked) {
                if (openedHere >= floor.entries()) {
                    break;
                }
                if (floor.atoms() > 0 && atomsOnThePageFrom(section) >= floor.atoms()) {
                    // The floor is already met by the section's own atoms. A
                    // section carrying both shapes — loose rows and entries —
                    // would otherwise reserve the total twice over.
                    break;
                }
                // An entry already on the page counts towards the floor: it is
                // there, from a lock or from an earlier pass, and reserving a
                // second one beside it would print more than the floor asks for.
                if (openEntries.contains(entry.entryId())) {
                    openedHere++;
                    continue;
                }
                // At least one, whatever the floor asks for. EDUCATION's floor
                // is "one entry and no bullets" -- a degree line is a heading
                // and asking for an achievement under it is asking to pad
                // (Bolum 20.2) -- and a bound of zero took nothing at all, so
                // the one section whose floor is only an entry was the one
                // section the floors could not put on the page.
                int wanted = Math.max(1, Math.max(floor.atomsPerEntry(), minAtomsFor(entry)));
                if (openWholeEntry(entry, wanted)) {
                    openedHere++;
                }
            }
        }

        /**
         * One entry, opened whole or not at all.
         *
         * <p><strong>Bolum 20.3, and the golden set is what said so.</strong>
         * Taking bullets one at a time while the budget lasted opened an entry
         * with two of the three it is worth printing at, and then the floor
         * held it there — {@link #dropEntry} will not take an entry the floor
         * put on the page. "Reaches its minimum or is dropped whole" is the
         * rule, and a stage that can leave an entry short has to check before
         * it starts rather than repair afterwards.
         *
         * @return whether the entry was opened
         */
        private boolean openWholeEntry(EntryPlan entry, int wanted) {
            List<AtomCandidate> take = new ArrayList<>();
            double needed = 0;
            for (AtomCandidate atom : sortedByScore(entry.atoms())) {
                if (take.size() >= wanted) {
                    break;
                }
                if (!pool.containsKey(atom.atomId())) {
                    continue;
                }
                // Only the first pays the furniture: it is what opens the
                // entry, and the heading is not charged twice.
                needed += take.isEmpty() ? effectiveCostOf(atom) : atom.renderCostPt();
                take.add(atom);
            }
            if (take.size() < wanted || needed > remainingPt()) {
                return false;
            }
            for (AtomCandidate atom : take) {
                include(atom, false);
                reservedByFloor.add(atom.atomId());
            }
            return true;
        }

        /** The floor never overspends the page; what does not fit waits. */
        private void takeIfItFits(AtomCandidate atom) {
            if (!pool.containsKey(atom.atomId())) {
                return;
            }
            if (effectiveCostOf(atom) <= remainingPt()) {
                include(atom, false);
                reservedByFloor.add(atom.atomId());
            }
        }

        /**
         * An entry's minimum, never above what its section may print.
         *
         * <p>The two can disagree, and one stored row is why this exists: an
         * About entry carrying four summaries had a minimum of two, so Bolum
         * 20.3's "prints its minimum or none of itself" put two opening
         * paragraphs on a page whose section may hold one. {@code V7} repairs
         * the rows; this stops any that are left — or any a client sends later
         * — from reintroducing it. The ceiling is about what the document is;
         * the minimum is about what an entry is worth, and the document wins.
         */
        private int minAtomsFor(EntryPlan entry) {
            int minimum = entry.minAtoms();
            SectionPlan section = planOfSection.get(sectionOfEntry.get(entry.entryId()));
            if (section == null || section.floor().maxAtoms() == 0) {
                return minimum;
            }
            return Math.min(minimum, section.floor().maxAtoms());
        }

        /**
         * Whether this atom's section may take another (Bolum 33.4).
         *
         * <p>Only {@code ABOUT} has a ceiling, and it is not a budget rule. A
         * real profile keeps four summaries — one written towards backend work,
         * one towards data, one towards AI — and every one of them scores the
         * same against a posting, so the greedy pass printed whichever two fit
         * and the page carried two opening paragraphs. A CV has one.
         */
        private boolean withinItsCeiling(AtomCandidate atom) {
            SectionPlan section = planOfSection.get(sectionOfAtom.get(atom.atomId()));
            if (section == null || section.floor().isNone()) {
                return true;
            }
            return section.floor().allowsMoreThan(atomsOnThePageFrom(section));
        }

        /** How much of this section is on the page, counted across both shapes. */
        private int atomsOnThePageFrom(SectionPlan section) {
            int total = takenFromSection.getOrDefault(section.sectionId(), 0);
            for (EntryPlan entry : section.entries()) {
                total += takenFromEntry.getOrDefault(entry.entryId(), 0);
            }
            return total;
        }

        /**
         * An entry named by what it says rather than by the id it was given.
         *
         * <p>The best-scoring wording in it, which is the one the ranking above
         * compared. Empty for an entry with no candidates at all, and the id
         * behind it is then the last resort it always was.
         */
        private static String wordingOf(EntryPlan entry) {
            return entry.atoms().stream()
                    .max(Comparator.comparingDouble(AtomCandidate::score)
                            .thenComparing(AtomCandidate::tieBreak))
                    .map(AtomCandidate::tieBreak)
                    .orElse("");
        }

        private static double bestScoreIn(EntryPlan entry) {
            return entry.atoms().stream()
                    .mapToDouble(AtomCandidate::score)
                    .max()
                    .orElse(0);
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
                    if (!withinItsCeiling(atom)) {
                        continue;
                    }
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
                if (takenFromEntry.getOrDefault(entryId, 0) < minAtomsFor(entry)) {
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
                    .filter(this::withinItsCeiling)
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
                    // Directly under the heading, so the heading's own space
                    // has already been left and this list adds almost nothing.
                    cost += capacity.fixedCost(CapacityModel.SECTION_LIST_OVERHEAD);
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
                                capacity.fixedCost(CapacityModel.SECTION_LIST_OVERHEAD));
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
                if (takenFromEntry.getOrDefault(entryId, 0) >= minAtomsFor(entry)) {
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
                if (chosen.forcedByLock() || reservedByFloor.contains(chosen.atomId())) {
                    // A locked atom keeps its entry alive whatever the minimum
                    // says: the user asked for it by name. So does one holding
                    // a section's floor -- dropping the entry would drop the
                    // section, which is the one outcome the floor rules out.
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
                if (reservedByFloor.contains(chosen.atomId())) {
                    // The whole point of the floor. Every atom holding a
                    // section's shape scores below the atoms competing for the
                    // rest of the page -- that is why the section needed a
                    // floor -- so an unguarded swap pass trades all six of them
                    // away and puts the page back where it started.
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
