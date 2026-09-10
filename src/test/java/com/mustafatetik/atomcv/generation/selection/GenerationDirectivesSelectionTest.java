package com.mustafatetik.atomcv.generation.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.mustafatetik.atomcv.generation.selection.SelectionRequest.AtomCandidate;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.EntryPlan;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.SectionPlan;
import com.mustafatetik.atomcv.generation.selection.SelectionState.RejectedAtom;
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
 * What a hand edit does to Faz C (Bolum 24.4).
 *
 * <p>Every directive here is asserted twice: once with it and once without.
 * A selection that would have come out the same either way proves nothing
 * about the directive, and on a page this full it very nearly does
 * (spec/12-quality.md § 51.7).
 */
class GenerationDirectivesSelectionTest {

    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

    /** Roughly one wrapped bullet at ten points. */
    private static final double BULLET_PT = 25.0;

    // ── exclusion ─────────────────────────────────────────────────────────

    @Test
    void anExcludedAtomIsNotOnThePage() {
        var atoms = atoms(4, 0.9);
        UUID unwanted = atoms.get(0).atomId();

        assertThat(idsOf(select(atoms, GenerationDirectives.none())))
                .as("without the edit it is chosen, or the test below proves nothing")
                .contains(unwanted);

        assertThat(idsOf(select(atoms, excluding(unwanted))))
                .doesNotContain(unwanted);
    }

    @Test
    void anExcludedAtomSaysWhyItIsMissing() {
        var atoms = atoms(4, 0.9);
        UUID unwanted = atoms.get(0).atomId();

        var state = select(atoms, excluding(unwanted));

        assertThat(state.rejected())
                .filteredOn(rejection -> rejection.atomId().equals(unwanted))
                .extracting(RejectedAtom::reason)
                .containsExactly(RejectionReason.EXCLUDED_BY_DIRECTIVE);
    }

    @Test
    void aProfileSwitchOutranksAnEdit() {
        var atoms = new ArrayList<>(atoms(4, 0.9));
        AtomCandidate off = atoms.get(0);
        atoms.set(0, new AtomCandidate(off.atomId(), off.variantId(), off.entryId(),
                off.score(), off.renderCostPt(), false, false));

        var state = select(atoms, excluding(off.atomId()));

        // Both are true of it; the reason reported is the one that survives
        // undoing the edit.
        assertThat(state.rejected())
                .filteredOn(rejection -> rejection.atomId().equals(off.atomId()))
                .extracting(RejectedAtom::reason)
                .containsExactly(RejectionReason.INACTIVE);
    }

    @Test
    void anExclusionBeatsALock() {
        var atoms = new ArrayList<>(atoms(4, 0.9));
        AtomCandidate locked = atoms.get(0);
        atoms.set(0, new AtomCandidate(locked.atomId(), locked.variantId(), locked.entryId(),
                locked.score(), locked.renderCostPt(), true, true));

        assertThat(idsOf(select(atoms, GenerationDirectives.none())))
                .contains(locked.atomId());

        // The lock is a standing preference and the edit is a decision about
        // this document, made later and about less. The later, narrower one
        // wins; a lock that could not be overruled would make the toggle a
        // suggestion.
        assertThat(idsOf(select(atoms, excluding(locked.atomId()))))
                .doesNotContain(locked.atomId());
    }

    // ── inclusion ─────────────────────────────────────────────────────────

    @Test
    void anIncludedAtomTakesRoomFromBetterOnes() {
        var atoms = new ArrayList<>(atoms(60, 0.9));
        AtomCandidate weakest = atoms.get(59);
        atoms.set(59, new AtomCandidate(weakest.atomId(), weakest.variantId(), weakest.entryId(),
                0.01, weakest.renderCostPt(), false, true));

        assertThat(idsOf(select(atoms, GenerationDirectives.none())))
                .as("the page is full and it scores worst, so nothing but the edit puts it there")
                .doesNotContain(weakest.atomId());

        assertThat(idsOf(select(atoms, including(weakest.atomId()))))
                .contains(weakest.atomId());
    }

    @Test
    void anIncludedAtomIsRecordedAsForced() {
        var atoms = new ArrayList<>(atoms(60, 0.9));
        AtomCandidate weakest = atoms.get(59);
        atoms.set(59, new AtomCandidate(weakest.atomId(), weakest.variantId(), weakest.entryId(),
                0.01, weakest.renderCostPt(), false, true));

        var state = select(atoms, including(weakest.atomId()));

        assertThat(state.selected())
                .filteredOn(atom -> atom.atomId().equals(weakest.atomId()))
                .extracting(SelectedAtom::forcedByLock)
                .containsExactly(true);
    }

    @Test
    void theGuaranteeSurvivesAnEdit() {
        var atoms = new ArrayList<>(atoms(60, 0.9));
        var wanted = new ArrayList<UUID>();
        for (int index = 50; index < 60; index++) {
            wanted.add(atoms.get(index).atomId());
        }

        var state = select(atoms, new GenerationDirectives(wanted, List.of()));

        assertThat(state.budget().usedPt() + state.budget().fixedPt())
                .isLessThanOrEqualTo(CAPACITY.pageTextHeightPt());
    }

    // ── the record itself ─────────────────────────────────────────────────

    @Test
    void anAtomIsEitherIncludedOrExcluded() {
        UUID atomId = UUID.randomUUID();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GenerationDirectives(List.of(atomId), List.of(atomId)));
    }

    @Test
    void theListsKeepTheirOrderAndDropTheirRepeats() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        var directives = new GenerationDirectives(
                List.of(first, second, first), List.of());

        assertThat(directives.includeAtoms()).containsExactly(first, second);
    }

    @Test
    void aShrinkingBudgetCarriesTheEditWithIt() {
        var atoms = atoms(4, 0.9);
        UUID unwanted = atoms.get(0).atomId();

        var shrunk = requestOf(atoms, excluding(unwanted)).withBudgetFactor(0.5);

        assertThat(idsOf(SelectionPhase.select(shrunk).orElseThrow()))
                .doesNotContain(unwanted);
    }

    @Test
    void noDirectivesSelectWhatNoneDoes() {
        var atoms = atoms(20, 0.9);

        assertThat(idsOf(select(atoms, GenerationDirectives.none())))
                .isEqualTo(idsOf(SelectionPhase.select(new SelectionRequest(
                        List.of(new SectionPlan(SECTION_ID, false, List.of(), atoms)),
                        1, CAPACITY)).orElseThrow()));
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /** Fixed, so the two runs of a comparison differ by the directive alone. */
    private static final UUID SECTION_ID = UUID.randomUUID();

    private static GenerationDirectives excluding(UUID atomId) {
        return new GenerationDirectives(List.of(), List.of(atomId));
    }

    private static GenerationDirectives including(UUID atomId) {
        return new GenerationDirectives(List.of(atomId), List.of());
    }

    private static List<AtomCandidate> atoms(int count, double score) {
        var atoms = new ArrayList<AtomCandidate>();
        for (int index = 0; index < count; index++) {
            atoms.add(new AtomCandidate(UUID.randomUUID(), UUID.randomUUID(), null,
                    score - index * 0.001, BULLET_PT, false, true));
        }
        return List.copyOf(atoms);
    }

    private static SelectionRequest requestOf(
            List<AtomCandidate> atoms, GenerationDirectives directives) {

        return new SelectionRequest(
                List.of(new SectionPlan(SECTION_ID, false, List.<EntryPlan>of(), atoms)),
                1, CAPACITY, directives);
    }

    private static SelectionState select(
            List<AtomCandidate> atoms, GenerationDirectives directives) {

        return SelectionPhase.select(requestOf(atoms, directives)).orElseThrow();
    }

    private static List<UUID> idsOf(SelectionState state) {
        return state.selected().stream().map(SelectedAtom::atomId).toList();
    }
}
