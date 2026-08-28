package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.generation.selection.SelectionPhase;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest;
import com.mustafatetik.atomcv.generation.selection.SelectionRequestBuilder;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.seed.GoldenProfile;
import com.mustafatetik.atomcv.profile.seed.GoldenProfileReader;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Three of the four tests Bolum 51.2 calls the most valuable ones, run across
 * the golden set (Bolum 51.3).
 *
 * <p>No database and no compiler: what is under test is the arithmetic that
 * makes the page promise, and it has to hold for every profile in the set at
 * every page limit, not for one hand-built fixture. The fourth test —
 * multi-tenant isolation — needs real HTTP and lives in
 * {@code MultiTenantIsolationIT}.
 */
class GoldenSelectionTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);
    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

    static Stream<Arguments> everyProfileAtEveryLimit() {
        return GoldenProfileReader.NAMES.stream().flatMap(name ->
                Stream.of("en", "tr").flatMap(language ->
                        Stream.of(1, 2).map(pages ->
                                Arguments.of(name, language, pages))));
    }

    // ── 1. the page limit is never exceeded ───────────────────────────────

    @ParameterizedTest(name = "{0}, {1}, {2} page(s)")
    @MethodSource("everyProfileAtEveryLimit")
    void theSelectionNeverExceedsThePage(String name, String language, int pages) {
        var golden = GoldenProfileReader.read(name, OWNER);
        var state = select(golden, language, pages).orElseThrow();

        assertThat(state.budget().usedPt())
                .as("content fits the free budget")
                .isLessThanOrEqualTo(state.budget().freePt());
        assertThat(state.budget().fixedPt() + state.budget().usedPt())
                .as("everything fits the page")
                .isLessThanOrEqualTo(pages * CAPACITY.pageTextHeightPt());
    }

    @ParameterizedTest(name = "{0}, {1}, {2} page(s)")
    @MethodSource("everyProfileAtEveryLimit")
    void everyAtomIsEitherSelectedOrGivenAReason(String name, String language, int pages) {
        var golden = GoldenProfileReader.read(name, OWNER);
        var request = request(golden, language, pages);
        var state = SelectionPhase.select(request).orElseThrow();

        int candidates = request.sections().stream()
                .mapToInt(section -> section.atoms().size()
                        + section.entries().stream().mapToInt(entry -> entry.atoms().size()).sum())
                .sum();

        assertThat(state.selected().size() + state.rejected().size()).isEqualTo(candidates);
    }

    @Test
    void moreRoomNeverMeansLessContent() {
        for (String name : GoldenProfileReader.NAMES) {
            var golden = GoldenProfileReader.read(name, OWNER);
            int onOnePage = select(golden, "en", 1).orElseThrow().selected().size();
            int onTwoPages = select(golden, "en", 2).orElseThrow().selected().size();

            assertThat(onTwoPages).as(name).isGreaterThanOrEqualTo(onOnePage);
        }
    }

    // ── 2. determinism (Bolum 51.2, Bolum 19.6) ───────────────────────────

    @Test
    void fiftyRunsOfEveryProfileGiveTheSameAnswer() {
        for (String name : GoldenProfileReader.NAMES) {
            var golden = GoldenProfileReader.read(name, OWNER);
            var request = request(golden, "en", 1);
            List<UUID> first = idsOf(SelectionPhase.select(request).orElseThrow());

            for (int run = 0; run < 50; run++) {
                assertThat(idsOf(SelectionPhase.select(request).orElseThrow()))
                        .as("%s, run %d", name, run)
                        .isEqualTo(first);
            }
        }
    }

    /**
     * Ids are minted as the fixture is read, and Bolum 19.6 breaks ties by id
     * — so a re-read can swap two atoms that score and cost exactly the same.
     * What may not move is how much of the page is used and how many atoms it
     * took (EK D.8.9).
     */
    @Test
    void rereadingTheFixtureFillsThePageTheSameWay() {
        for (String name : GoldenProfileReader.NAMES) {
            var first = select(GoldenProfileReader.read(name, OWNER), "en", 1).orElseThrow();

            for (int run = 0; run < 5; run++) {
                var again = select(GoldenProfileReader.read(name, UUID.randomUUID()), "en", 1)
                        .orElseThrow();

                assertThat(again.selected().size()).as("%s, run %d", name, run)
                        .isEqualTo(first.selected().size());
                assertThat(again.budget().usedPt()).as("%s, run %d", name, run)
                        .isEqualTo(first.budget().usedPt());
            }
        }
    }

    // ── 4. locks and structural constraints ───────────────────────────────

    @ParameterizedTest(name = "{0}, {1}, {2} page(s)")
    @MethodSource("everyProfileAtEveryLimit")
    void locksAndStructuralConstraintsAreRespected(String name, String language, int pages) {
        var golden = GoldenProfileReader.read(name, OWNER);
        var state = select(golden, language, pages).orElseThrow();
        List<UUID> selected = idsOf(state);

        for (Atom atom : golden.atoms()) {
            if (atom.isAlwaysInclude() && atom.isActive()) {
                assertThat(selected).as("%s: a locked atom is on the page", name)
                        .contains(atom.getId());
            }
            if (!atom.isActive()) {
                assertThat(selected).as("%s: an atom switched off is not", name)
                        .doesNotContain(atom.getId());
            }
        }

        assertEntriesAreWholeOrAbsent(golden.tree(), selected, name);
    }

    @ParameterizedTest(name = "{0}, {1}, {2} page(s)")
    @MethodSource("everyProfileAtEveryLimit")
    void nothingFromAnInactiveSectionOrEntryReachesThePage(
            String name, String language, int pages) {

        var golden = GoldenProfileReader.read(name, OWNER);
        var state = select(golden, language, pages).orElseThrow();
        List<UUID> selected = idsOf(state);

        golden.tree().sections().forEach(section -> {
            boolean sectionOff = !section.section().isActive();
            section.atoms().forEach(atom -> {
                if (sectionOff) {
                    assertThat(selected).as("%s: inactive section", name)
                            .doesNotContain(atom.atom().getId());
                }
            });
            section.entries().forEach(entry -> {
                boolean entryOff = sectionOff || !entry.entry().isActive();
                entry.atoms().forEach(atom -> {
                    if (entryOff) {
                        assertThat(selected).as("%s: inactive entry", name)
                                .doesNotContain(atom.atom().getId());
                    }
                });
            });
        });
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static void assertEntriesAreWholeOrAbsent(
            ProfileTree tree, List<UUID> selected, String name) {

        tree.sections().forEach(section -> section.entries().forEach(entry -> {
            long taken = entry.atoms().stream()
                    .filter(atom -> selected.contains(atom.atom().getId()))
                    .count();
            if (taken > 0) {
                assertThat(taken)
                        .as("%s: an entry reaches its minimum or is dropped whole", name)
                        .isGreaterThanOrEqualTo(entry.entry().getMinAtoms());
            }
        }));
    }

    private static Result<SelectionState> select(
            GoldenProfile golden, String language, int pages) {

        return SelectionPhase.select(request(golden, language, pages));
    }

    private static SelectionRequest request(GoldenProfile golden, String language, int pages) {
        return SelectionRequestBuilder.build(golden.tree(), TemplateCustomization.CLASSIC,
                CAPACITY, pages, language, Tone.FORMAL, TODAY).request();
    }

    private static List<UUID> idsOf(SelectionState state) {
        return state.selected().stream().map(SelectedAtom::atomId).toList();
    }

}
