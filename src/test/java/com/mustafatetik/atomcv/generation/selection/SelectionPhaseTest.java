package com.mustafatetik.atomcv.generation.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.AtomCandidate;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.EntryPlan;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.SectionPlan;
import com.mustafatetik.atomcv.generation.selection.SelectionState.RejectionReason;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The product's central promise, checked (Bolum 20).
 *
 * <p>The measured capacity of the classic template is used rather than a made
 * up one: these are the numbers a real page has, so a test that passes here
 * says something about a real CV.
 */
class SelectionPhaseTest {

    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

    /** Roughly one wrapped bullet at ten points. */
    private static final double BULLET_PT = 25.0;

    // ── the guarantee ─────────────────────────────────────────────────────

    @Test
    void theSelectionNeverExceedsThePage() {
        var state = select(profileOf(6, 8, 0.5)).orElseThrow();

        assertThat(state.budget().usedPt() + state.budget().fixedPt())
                .isLessThanOrEqualTo(CAPACITY.pageTextHeightPt());
    }

    @Test
    void aSecondPageIsTwiceTheRoom() {
        var one = select(profileOf(6, 8, 0.5), 1).orElseThrow();
        var two = select(profileOf(6, 8, 0.5), 2).orElseThrow();

        assertThat(two.selected().size()).isGreaterThan(one.selected().size());
        assertThat(two.budget().totalPt()).isEqualTo(2 * CAPACITY.pageTextHeightPt());
    }

    /** Bolum 51.2, and the reason Faz C is code rather than a prompt. */
    @Test
    void theSameInputGivesTheSameAnswerFiftyTimes() {
        var request = profileOf(5, 6, 0.5);
        List<UUID> first = idsOf(select(request).orElseThrow());

        for (int run = 0; run < 50; run++) {
            assertThat(idsOf(select(request).orElseThrow()))
                    .as("run %d", run)
                    .isEqualTo(first);
        }
    }

    @Test
    void tiesAreBrokenByIdRatherThanByOrder() {
        // Identical scores and costs: only the tie-break decides, and it must
        // decide the same way whichever order they arrive in.
        var atoms = new ArrayList<AtomCandidate>();
        for (int index = 0; index < 6; index++) {
            atoms.add(atom(0.5, BULLET_PT, false));
        }
        var forwards = oneSection(atoms);
        var backwards = oneSection(atoms.reversed());

        assertThat(idsOf(select(forwards).orElseThrow()))
                .containsExactlyInAnyOrderElementsOf(idsOf(select(backwards).orElseThrow()));
    }

    // ── the user's locks ──────────────────────────────────────────────────

    @Test
    void aLockedAtomIsSelectedWhateverItScores() {
        var locked = atom(0.01, BULLET_PT, true);
        var atoms = new ArrayList<>(List.of(locked));
        for (int index = 0; index < 30; index++) {
            atoms.add(atom(0.9, BULLET_PT, false));
        }

        var state = select(oneSection(atoms)).orElseThrow();

        assertThat(idsOf(state)).contains(locked.atomId());
        assertThat(state.selected().stream()
                .filter(selected -> selected.atomId().equals(locked.atomId()))
                .findFirst().orElseThrow().forcedByLock()).isTrue();
    }

    @Test
    void anInactiveAtomIsNeverSelectedAndSaysWhy() {
        var off = new AtomCandidate(UUID.randomUUID(), UUID.randomUUID(), null,
                0.99, BULLET_PT, false, false);

        var state = select(oneSection(List.of(off, atom(0.5, BULLET_PT, false)))).orElseThrow();

        assertThat(idsOf(state)).doesNotContain(off.atomId());
        assertThat(state.rejected()).anySatisfy(rejection -> {
            assertThat(rejection.atomId()).isEqualTo(off.atomId());
            assertThat(rejection.reason()).isEqualTo(RejectionReason.INACTIVE);
        });
    }

    /** Bolum 20.3 stage 1: pinned content that cannot fit is a conflict, not a silent trim. */
    @Test
    void pinnedContentThatCannotFitIsRefusedWithSomethingToDo() {
        var atoms = new ArrayList<AtomCandidate>();
        for (int index = 0; index < 40; index++) {
            atoms.add(atom(0.5, BULLET_PT, true));
        }

        Result<SelectionState> result = select(oneSection(atoms));

        assertThat(result.isErr()).isTrue();
        var error = (PipelineError.ConflictingPreferences)
                ((Result.Err<SelectionState>) result).error();
        assertThat(error.pinnedPt()).isGreaterThan(error.budgetPt());
        assertThat(error.options())
                .extracting(com.mustafatetik.atomcv.shared.error.Resolution::action)
                .containsExactly(
                        com.mustafatetik.atomcv.shared.error.ResolutionAction.INCREASE_PAGE_LIMIT,
                        com.mustafatetik.atomcv.shared.error.ResolutionAction.REVIEW_PINS,
                        com.mustafatetik.atomcv.shared.error.ResolutionAction.KEEP_TOP_PINNED);
    }

    // ── structure ─────────────────────────────────────────────────────────

    @Test
    void anEntryShowsItsMinimumOrNoneOfItself() {
        // Two entries, room for about one and a half.
        var first = entry(3, 4, 0.9);
        var second = entry(3, 4, 0.2);
        var request = new SelectionRequest(
                List.of(new SectionPlan(UUID.randomUUID(), false, List.of(first, second), List.of())),
                1, smallCapacity());

        var state = select(request).orElseThrow();

        for (EntryPlan entry : List.of(first, second)) {
            long taken = entry.atoms().stream()
                    .filter(atom -> idsOf(state).contains(atom.atomId()))
                    .count();
            assertThat(taken)
                    .as("an entry is whole or absent")
                    .satisfiesAnyOf(
                            count -> assertThat(count).isZero(),
                            count -> assertThat(count).isGreaterThanOrEqualTo(3L));
        }
    }

    @Test
    void openingAnEntryCostsItsHeading() {
        // One atom in its own entry costs the atom, the entry heading and the
        // list it opens — constraint (5), the reason this is not a knapsack.
        UUID entryId = UUID.randomUUID();
        var single = new AtomCandidate(UUID.randomUUID(), UUID.randomUUID(), entryId,
                0.9, BULLET_PT, false, true);
        var request = new SelectionRequest(
                List.of(new SectionPlan(UUID.randomUUID(), false,
                        List.of(new EntryPlan(entryId, (short) 1, List.of(single))),
                        List.of())),
                1, CAPACITY);

        var state = select(request).orElseThrow();

        assertThat(state.budget().fixedPt()).isEqualTo(
                CAPACITY.fixedCost(CapacityModel.HEADER_BLOCK)
                        + CAPACITY.fixedCost(CapacityModel.SECTION_HEADER)
                        + CAPACITY.fixedCost(CapacityModel.ENTRY_HEADER)
                        + CAPACITY.fixedCost(CapacityModel.ITEMIZE_OVERHEAD));
    }

    /** Bolum 20.3: without decay one strong entry can take the whole page. */
    @Test
    void oneEntryDoesNotTakeTheWholePage() {
        var crowded = entry(1, 30, 0.80);
        var others = new ArrayList<EntryPlan>();
        others.add(crowded);
        for (int index = 0; index < 5; index++) {
            others.add(entry(1, 3, 0.70));
        }
        var request = new SelectionRequest(
                List.of(new SectionPlan(UUID.randomUUID(), false, others, List.of())),
                1, CAPACITY);

        var state = select(request).orElseThrow();

        long fromCrowded = crowded.atoms().stream()
                .filter(atom -> idsOf(state).contains(atom.atomId()))
                .count();
        assertThat(fromCrowded)
                .as("a higher-scoring entry still leaves room for the others")
                .isLessThan(state.selected().size());
    }

    @Test
    void everyAtomIsEitherSelectedOrGivenAReason() {
        var request = profileOf(4, 10, 0.5);
        var state = select(request).orElseThrow();

        int total = request.sections().stream()
                .mapToInt(section -> section.atoms().size()
                        + section.entries().stream().mapToInt(entry -> entry.atoms().size()).sum())
                .sum();

        assertThat(state.selected().size() + state.rejected().size()).isEqualTo(total);
    }

    @Test
    void theBudgetAddsUp() {
        var state = select(profileOf(5, 6, 0.5)).orElseThrow();

        assertThat(state.budget().freePt())
                .isEqualTo(state.budget().totalPt() - state.budget().fixedPt());
        assertThat(state.budget().usedPt()).isEqualTo(
                state.selected().stream().mapToDouble(SelectedAtom::renderCostPt).sum());
    }

    @Test
    void anEmptyProfileSelectsNothingAndStillBalances() {
        var state = select(new SelectionRequest(List.of(), 1, CAPACITY)).orElseThrow();

        assertThat(state.isEmpty()).isTrue();
        assertThat(state.budget().usedPt()).isZero();
        assertThat(state.budget().fixedPt())
                .isEqualTo(CAPACITY.fixedCost(CapacityModel.HEADER_BLOCK));
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private static Result<SelectionState> select(SelectionRequest request) {
        return SelectionPhase.select(request);
    }

    private static Result<SelectionState> select(SelectionRequest request, int maxPages) {
        return SelectionPhase.select(
                new SelectionRequest(request.sections(), maxPages, request.capacity()));
    }

    private static List<UUID> idsOf(SelectionState state) {
        return state.selected().stream().map(SelectedAtom::atomId).toList();
    }

    private static AtomCandidate atom(double score, double costPt, boolean locked) {
        return new AtomCandidate(UUID.randomUUID(), UUID.randomUUID(), null,
                score, costPt, locked, true);
    }

    private static EntryPlan entry(int minAtoms, int atomCount, double score) {
        UUID entryId = UUID.randomUUID();
        var atoms = new ArrayList<AtomCandidate>();
        for (int index = 0; index < atomCount; index++) {
            atoms.add(new AtomCandidate(UUID.randomUUID(), UUID.randomUUID(), entryId,
                    score, BULLET_PT, false, true));
        }
        return new EntryPlan(entryId, (short) minAtoms, atoms);
    }

    private static SelectionRequest oneSection(List<AtomCandidate> atoms) {
        return new SelectionRequest(
                List.of(new SectionPlan(UUID.randomUUID(), false, List.of(), atoms)),
                1, CAPACITY);
    }

    private static SelectionRequest profileOf(int entryCount, int atomsPerEntry, double score) {
        var entries = new ArrayList<EntryPlan>();
        for (int index = 0; index < entryCount; index++) {
            entries.add(entry(2, atomsPerEntry, score + index * 0.01));
        }
        return new SelectionRequest(
                List.of(new SectionPlan(UUID.randomUUID(), false, entries, List.of())),
                1, CAPACITY);
    }

    /** A page with room for a handful of bullets, to force the hard choices. */
    private static CapacityModel smallCapacity() {
        return new CapacityModel(220.0, CAPACITY.textWidthPt(), CAPACITY.baselineSkipPt(),
                CAPACITY.fixedCosts());
    }
}
