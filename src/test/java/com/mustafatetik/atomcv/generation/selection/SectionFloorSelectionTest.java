package com.mustafatetik.atomcv.generation.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.selection.SelectionRequest.AtomCandidate;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.EntryPlan;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.SectionPlan;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A CV has a shape before it has a ranking ({@link SectionFloor}).
 *
 * <p>The run that forced this put twenty atoms on a page and all twenty came
 * from Projects — no experience, no skills, no summary. Every one of those
 * choices was the right answer to "which atom is worth the most per point",
 * and the document was not a CV. These are the rules that were missing.
 */
class SectionFloorSelectionTest {

    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

    /** One printed line, which is what a measured bullet comes to. */
    private static final double LINE_PT = CAPACITY.fixedCost(CapacityModel.ITEM_LINE);

    /**
     * What a bullet of {@code n} printed lines costs, the way a measurement
     * comes out ({@code RenderCost.totalPt}).
     *
     * <p>Not {@code n * LINE_PT}. A bullet pays the list's separation once and
     * a baseline per line, so multiplying the one-line cost charges the
     * separation {@code n} times — which under the reference template, where a
     * bullet is a small line plus five points, prices a five-line paragraph a
     * third above what the compiler sets it in.
     */
    private static double linesPt(int lines) {
        return lines * CAPACITY.itemBaselineSkipPt() + CAPACITY.itemSpacingPt();
    }

    // ── the shape ─────────────────────────────────────────────────────────

    /**
     * <strong>The headline rule.</strong> Projects score far above everything
     * else and would take the whole page on efficiency alone. Every section the
     * profile has still reaches it.
     */
    @Test
    void everySectionTheProfileHasReachesThePage() {
        var profile = aWholeProfile();

        var state = SelectionPhase.select(profile.request()).orElseThrow();

        assertThat(profile.sectionsOn(state))
                .containsExactlyInAnyOrder(SectionKind.ABOUT, SectionKind.EDUCATION,
                        SectionKind.EXPERIENCE, SectionKind.PROJECTS,
                        SectionKind.SKILLS, SectionKind.LANGUAGES);
    }

    /**
     * And each of them at the depth its floor asks for (Bolum 20.3).
     *
     * <p>Two pages, and the reason is worth stating rather than hiding in a
     * parameter. This profile's six full floors come to more than one page of
     * the reference template holds: its furniture is dearer than the template
     * this fixture was first written against — a bullet is a small line plus
     * the separation an itemize sets, and a section heading below the first
     * costs seven points more — so a page of six sections carries about two
     * bullets fewer than it used to. What happens when they do not all fit is
     * a different rule and has its own tests below; this one is about the
     * floors being honoured when there is room for them.
     */
    @Test
    void eachSectionArrivesAtTheDepthItsFloorAsksFor() {
        var profile = aWholeProfile();

        var state = SelectionPhase.select(profile.request(2)).orElseThrow();

        assertThat(profile.atomsIn(state, SectionKind.ABOUT)).isGreaterThanOrEqualTo(1);
        assertThat(profile.entriesIn(state, SectionKind.EXPERIENCE))
                .as("two roles, and two bullets under each")
                .isGreaterThanOrEqualTo(2);
        assertThat(profile.atomsIn(state, SectionKind.EXPERIENCE)).isGreaterThanOrEqualTo(4);
        assertThat(profile.entriesIn(state, SectionKind.PROJECTS)).isGreaterThanOrEqualTo(2);
        assertThat(profile.atomsIn(state, SectionKind.PROJECTS))
                .as("two projects, and three bullets under each")
                .isGreaterThanOrEqualTo(6);
        assertThat(profile.atomsIn(state, SectionKind.SKILLS)).isGreaterThanOrEqualTo(3);
        assertThat(profile.atomsIn(state, SectionKind.LANGUAGES)).isGreaterThanOrEqualTo(2);
    }

    /**
     * On one page, this profile runs out — and it runs out the way it should.
     *
     * <p>Every section still reaches the page and every one of them keeps at
     * least its hard floor; what gives is the depth of the sections a reader
     * looks for later. The page is full to within a bullet, which is what says
     * this is the budget rather than the floors failing.
     */
    @Test
    void apageThatCannotHoldEveryFullFloorStillHoldsEverySection() {
        var profile = aWholeProfile();

        var state = SelectionPhase.select(profile.request()).orElseThrow();

        assertThat(profile.sectionsOn(state)).hasSize(6);
        assertThat(profile.entriesIn(state, SectionKind.EXPERIENCE)).isGreaterThanOrEqualTo(2);
        assertThat(profile.atomsIn(state, SectionKind.EXPERIENCE)).isGreaterThanOrEqualTo(2);
        assertThat(state.budget().freePt() - state.budget().usedPt())
                .as("full to within a bullet")
                .isLessThan(CAPACITY.fixedCost(CapacityModel.ITEM_LINE));
    }

    /**
     * A floor is a ceiling on what may be <em>reserved</em>, not a quota. What
     * is left over still competes, so a posting that is all about the projects
     * still gets a page weighted towards them.
     */
    @Test
    void whatIsLeftAfterTheFloorsStillGoesToWhatScoresBest() {
        var profile = aWholeProfile();

        // Two pages, so that there is something left over to go anywhere. On
        // one there is not: see the test above.
        var state = SelectionPhase.select(profile.request(2)).orElseThrow();

        assertThat(profile.atomsIn(state, SectionKind.PROJECTS))
                .as("the floor reserved six; the rest of the page went the same way")
                .isGreaterThan(6);
    }

    // ── what the profile does not have ────────────────────────────────────

    /**
     * A section that is not in the profile is not on the page. A floor never
     * invents a heading to sit above nothing — that is the failure this whole
     * product is built not to have.
     */
    @Test
    void asectionTheProfileDoesNotHaveIsNotPrinted() {
        var profile = new Fixture();
        profile.entrySection(SectionKind.EXPERIENCE, 3, 4, 0.5);
        profile.looseSection(SectionKind.SKILLS, 4, 0.5);

        var state = SelectionPhase.select(profile.request()).orElseThrow();

        assertThat(profile.sectionsOn(state))
                .containsExactlyInAnyOrder(SectionKind.EXPERIENCE, SectionKind.SKILLS);
    }

    /**
     * And one that is there but thinner than its floor contributes all of what
     * it has. A person with one job has one job: the floor asks for two and
     * takes the one, rather than refusing the section for being short.
     */
    @Test
    void asectionThinnerThanItsFloorContributesEverythingItHas() {
        var profile = new Fixture();
        profile.entrySection(SectionKind.EXPERIENCE, 1, 1, 0.5);
        profile.looseSection(SectionKind.LANGUAGES, 1, 0.5);

        var state = SelectionPhase.select(profile.request()).orElseThrow();

        assertThat(profile.entriesIn(state, SectionKind.EXPERIENCE)).isEqualTo(1);
        assertThat(profile.atomsIn(state, SectionKind.EXPERIENCE)).isEqualTo(1);
        assertThat(profile.atomsIn(state, SectionKind.LANGUAGES)).isEqualTo(1);
    }

    // ── the same kind, hung two different ways ────────────────────────────

    /**
     * <strong>A floor says how much, never where.</strong> One import writes
     * the summary as a paragraph straight under the heading; another puts it in
     * a "Professional Summary" entry. One writes two languages as two lines,
     * another as two entries. The page is the same either way and so is the
     * floor.
     *
     * <p>Measured on a real profile before it was written: every section of an
     * 84-atom import hung its atoms off entries, and About and Languages — the
     * two given loose-atom floors — were the two sections that still did not
     * reach the page.
     */
    @Test
    void asectionWhoseAtomsHangOffEntriesGetsTheSameFloor() {
        var inEntries = new Fixture();
        inEntries.entrySection(SectionKind.ABOUT, 1, 4, 0.20);
        inEntries.entrySection(SectionKind.LANGUAGES, 2, 1, 0.15);
        inEntries.entrySection(SectionKind.PROJECTS, 14, 5, 0.90);

        var state = SelectionPhase.select(inEntries.request()).orElseThrow();

        assertThat(inEntries.sectionsOn(state))
                .contains(SectionKind.ABOUT, SectionKind.LANGUAGES);
        assertThat(inEntries.atomsIn(state, SectionKind.ABOUT)).isGreaterThanOrEqualTo(1);
        assertThat(inEntries.atomsIn(state, SectionKind.LANGUAGES))
                .as("two languages, one line each")
                .isGreaterThanOrEqualTo(2);
    }

    /**
     * And a section carrying both shapes reserves the total once. Counting the
     * loose rows and then opening as many entries beside them would print twice
     * the floor and spend the page on one section.
     */
    @Test
    void asectionCarryingBothShapesReservesItsFloorOnce() {
        var profile = new Fixture();
        var mixed = profile.mixedSection(SectionKind.SKILLS, 3, 3, 0.20);
        profile.entrySection(SectionKind.PROJECTS, 14, 5, 0.90);

        var state = SelectionPhase.select(profile.request()).orElseThrow();

        assertThat(profile.atomsIn(state, SectionKind.SKILLS))
                .as("three is the floor, and the entries do not add three more")
                .isEqualTo(3);
        assertThat(mixed.floor().atoms()).isEqualTo(3);
    }

    /**
     * <strong>About is one paragraph, and the ceiling is why.</strong> A real
     * profile keeps four summaries — one written towards backend work, one
     * towards data, one towards AI — and they all score the same against any
     * one posting, so the greedy pass printed whichever two fit and the page
     * opened with two competing paragraphs.
     */
    @Test
    void aboutPrintsOneParagraphHoweverManyTheProfileKeeps() {
        var profile = new Fixture();
        profile.entrySection(SectionKind.ABOUT, 1, 4, 0.95);
        profile.entrySection(SectionKind.EXPERIENCE, 2, 2, 0.20);

        var state = SelectionPhase.select(profile.request()).orElseThrow();

        assertThat(profile.atomsIn(state, SectionKind.ABOUT))
                .as("four to choose from, one on the page")
                .isEqualTo(1);
    }

    /**
     * And a stored entry minimum cannot argue the ceiling away. An About entry
     * carrying four summaries was written with a minimum of two, so Bolum
     * 20.3's "prints its minimum or none of itself" put two opening paragraphs
     * on a page whose section may hold one.
     */
    @Test
    void astoredEntryMinimumDoesNotOverrideTheCeiling() {
        var profile = new Fixture();
        profile.entrySection(SectionKind.ABOUT, 1, 4, 0.95, (short) 2);
        profile.entrySection(SectionKind.EXPERIENCE, 2, 2, 0.20);

        var state = SelectionPhase.select(profile.request()).orElseThrow();

        assertThat(profile.atomsIn(state, SectionKind.ABOUT)).isEqualTo(1);
        assertThat(profile.sectionsOn(state)).contains(SectionKind.ABOUT);
    }

    /** And a kind with no ceiling takes as much as the page and the score allow. */
    @Test
    void akindWithNoCeilingIsNotCapped() {
        var profile = new Fixture();
        profile.entrySection(SectionKind.EXPERIENCE, 2, 6, 0.95);

        var state = SelectionPhase.select(profile.request()).orElseThrow();

        assertThat(profile.atomsIn(state, SectionKind.EXPERIENCE)).isGreaterThan(4);
    }

    // ── the sections the order does not name ──────────────────────────────

    /**
     * A person who wrote a "Certifications" section did not tell us where it
     * goes. It has no floor and competes on score alone, which is the posting
     * deciding rather than a guess made from a heading.
     */
    @Test
    void asectionTheOrderDoesNotNameCompetesOnScoreAlone() {
        var profile = new Fixture();
        profile.entrySection(SectionKind.EXPERIENCE, 2, 2, 0.30);
        var custom = profile.looseSection(SectionKind.CUSTOM, 4, 0.95);

        var state = SelectionPhase.select(profile.request()).orElseThrow();

        assertThat(profile.atomsIn(state, SectionKind.CUSTOM))
                .as("nothing reserved it, and it scored its way on regardless")
                .isPositive();
        assertThat(custom.priority()).isEqualTo(SectionFloor.UNRANKED);
    }

    // ── the guarantee the floors may not break ────────────────────────────

    /**
     * <strong>The page limit still wins.</strong> The floors are reservations
     * against a budget, never a demand on it: a profile whose floors cannot all
     * fit prints thinner sections, and never a longer document than was asked
     * for.
     */
    @Test
    void thefloorsNeverSpendMoreThanThePageHas() {
        var profile = aWholeProfile();

        var state = SelectionPhase.select(profile.request()).orElseThrow();

        assertThat(state.budget().usedPt() + state.budget().fixedPt())
                .isLessThanOrEqualTo(CAPACITY.pageTextHeightPt());
    }

    /**
     * And where the full floors cannot all fit, every section still arrives —
     * thinner. Dropping one is not among the outcomes: a CV missing its
     * experience is not a smaller CV, it is a different document.
     *
     * <p>Two thirds of a page is chosen against the arithmetic rather than for
     * roundness. The six full floors come to 576 pt and their hard floors —
     * one line, or one entry, each — to 386 pt; 472 pt sits between, which is
     * the band this rule is about.
     */
    @Test
    void apageTooSmallForEveryFloorPrintsThinnerSectionsRatherThanFewer() {
        var profile = aWholeProfile();
        CapacityModel narrow = pageOf(2.0 / 3);

        var state = SelectionPhase.select(profile.request(narrow)).orElseThrow();

        assertThat(profile.sectionsOn(state))
                .containsExactlyInAnyOrder(SectionKind.ABOUT, SectionKind.EDUCATION,
                        SectionKind.EXPERIENCE, SectionKind.PROJECTS,
                        SectionKind.SKILLS, SectionKind.LANGUAGES);
        assertThat(profile.atomsIn(state, SectionKind.PROJECTS))
                .as("thinner than the floor asked for, and still there")
                .isLessThan(6);
        assertThat(state.budget().usedPt() + state.budget().fixedPt())
                .isLessThanOrEqualTo(narrow.pageTextHeightPt());
    }

    /**
     * <strong>Below that band the two rules genuinely disagree, and the page
     * limit wins.</strong> Half a page cannot hold one line of each of six
     * sections — their hard floors come to 386 pt against 354 — so something
     * has to give, and it is the section rather than the page: a document
     * longer than the person asked for is the guarantee this product is built
     * on, and a heading with nothing under it is not a section anyway.
     *
     * <p>Written down because it is the one case where "every section is
     * printed" is not kept, and a rule with an unrecorded exception is a rule
     * somebody will be surprised by.
     */
    @Test
    void apageTooSmallForEvenTheHardFloorsKeepsThePageAndNotTheSection() {
        var profile = aWholeProfile();
        CapacityModel tiny = pageOf(0.5);

        var state = SelectionPhase.select(profile.request(tiny)).orElseThrow();

        assertThat(profile.sectionsOn(state))
                .as("the ones the order puts first, and not all six")
                .contains(SectionKind.ABOUT, SectionKind.EDUCATION, SectionKind.EXPERIENCE)
                .hasSizeLessThan(6);
        assertThat(state.budget().usedPt() + state.budget().fixedPt())
                .as("and the page is still a page")
                .isLessThanOrEqualTo(tiny.pageTextHeightPt());
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    /**
     * The shape the real profile has, with Projects scoring far above the rest
     * — which is the run that produced a page of nothing but projects.
     */
    private static Fixture aWholeProfile() {
        var profile = new Fixture();
        profile.looseSection(SectionKind.ABOUT, 1, 0.30, linesPt(5));
        profile.headingSection(SectionKind.EDUCATION, 1, 0.30);
        profile.entrySection(SectionKind.EXPERIENCE, 3, 6, 0.35);
        profile.entrySection(SectionKind.PROJECTS, 14, 5, 0.90);
        profile.looseSection(SectionKind.SKILLS, 7, 0.30, linesPt(2));
        profile.looseSection(SectionKind.LANGUAGES, 2, 0.20);
        return profile;
    }

    /** The classic page, at a fraction of its height. */
    private static CapacityModel pageOf(double share) {
        return new CapacityModel(CAPACITY.pageTextHeightPt() * share, CAPACITY.textWidthPt(),
                CAPACITY.baselineSkipPt(), CAPACITY.itemBaselineSkipPt(), fixedCosts());
    }

    private static Map<String, Double> fixedCosts() {
        var costs = new LinkedHashMap<String, Double>();
        for (String name : List.of(CapacityModel.HEADER_BLOCK, CapacityModel.SECTION_HEADER,
                CapacityModel.ENTRY_HEADER, CapacityModel.ENTRY_HEADER_AFTER_LIST,
                CapacityModel.ITEMIZE_OVERHEAD, CapacityModel.SECTION_LIST_OVERHEAD,
                CapacityModel.SECTION_LIST_CLOSE,
                CapacityModel.ITEM_LINE, CapacityModel.SECTION_ITEM_LINE,
                CapacityModel.INLINE_ROW, CapacityModel.INLINE_LIST_OVERHEAD,
                CapacityModel.PARAGRAPH_LIST_OVERHEAD, CapacityModel.PROJECT_HEADING,
                CapacityModel.PROJECT_HEADING_AFTER_LIST)) {
            costs.put(name, CAPACITY.fixedCost(name));
        }
        return costs;
    }

    /** A profile as section plans, remembering which kind each id belongs to. */
    private static final class Fixture {

        private final List<SectionPlan> sections = new ArrayList<>();
        private final Map<UUID, SectionKind> kindOfSection = new LinkedHashMap<>();
        private final Map<UUID, SectionKind> kindOfAtom = new LinkedHashMap<>();
        private final Map<UUID, UUID> entryOfAtom = new LinkedHashMap<>();

        SectionPlan looseSection(SectionKind kind, int rows, double score) {
            return looseSection(kind, rows, score, LINE_PT);
        }

        /** Rows straight under the heading — a skills matrix, a summary. */
        SectionPlan looseSection(SectionKind kind, int rows, double score, double costEach) {
            var atoms = new ArrayList<AtomCandidate>();
            for (int index = 0; index < rows; index++) {
                atoms.add(atom(kind, null, score - index * 0.001, costEach));
            }
            return add(kind, List.of(), atoms);
        }

        /** Entries with bullets under them — experience, projects. */
        SectionPlan entrySection(SectionKind kind, int entries, int bullets, double score) {
            return entrySection(kind, entries, bullets, score, (short) 0);
        }

        SectionPlan entrySection(SectionKind kind, int entries, int bullets, double score,
                short minAtoms) {
            var plans = new ArrayList<EntryPlan>();
            for (int index = 0; index < entries; index++) {
                UUID entryId = UUID.randomUUID();
                var atoms = new ArrayList<AtomCandidate>();
                for (int bullet = 0; bullet < bullets; bullet++) {
                    atoms.add(atom(kind, entryId,
                            score - index * 0.01 - bullet * 0.001, LINE_PT));
                }
                plans.add(new EntryPlan(entryId, minAtoms, atoms));
            }
            return add(kind, plans, List.of());
        }

        /** Loose rows and entries in one section, which a real profile can have. */
        SectionPlan mixedSection(SectionKind kind, int rows, int entries, double score) {
            var atoms = new ArrayList<AtomCandidate>();
            for (int index = 0; index < rows; index++) {
                atoms.add(atom(kind, null, score - index * 0.001, LINE_PT));
            }
            var plans = new ArrayList<EntryPlan>();
            for (int index = 0; index < entries; index++) {
                UUID entryId = UUID.randomUUID();
                plans.add(new EntryPlan(entryId, (short) 0,
                        List.of(atom(kind, entryId, score - 0.05 - index * 0.001, LINE_PT))));
            }
            return add(kind, plans, atoms);
        }

        /** Entries with nothing under them — a degree line (Bolum 20.2). */
        SectionPlan headingSection(SectionKind kind, int entries, double score) {
            var plans = new ArrayList<EntryPlan>();
            for (int index = 0; index < entries; index++) {
                UUID entryId = UUID.randomUUID();
                var heading = AtomCandidate.forEntryHeader(entryId, score, "degree" + index);
                kindOfAtom.put(heading.atomId(), kind);
                entryOfAtom.put(heading.atomId(), entryId);
                plans.add(new EntryPlan(entryId, (short) 0, List.of(heading)));
            }
            return add(kind, plans, List.of());
        }

        private SectionPlan add(SectionKind kind, List<EntryPlan> entries,
                List<AtomCandidate> atoms) {

            UUID sectionId = UUID.randomUUID();
            kindOfSection.put(sectionId, kind);
            var plan = new SectionPlan(sectionId, false, SectionFloor.priorityOf(kind),
                    SectionFloor.forKind(kind), entries, atoms);
            sections.add(plan);
            return plan;
        }

        private AtomCandidate atom(SectionKind kind, UUID entryId, double score, double cost) {
            UUID atomId = UUID.randomUUID();
            kindOfAtom.put(atomId, kind);
            if (entryId != null) {
                entryOfAtom.put(atomId, entryId);
            }
            return new AtomCandidate(atomId, UUID.randomUUID(), entryId, score, cost,
                    false, true, atomId.toString());
        }

        SelectionRequest request() {
            return request(CAPACITY);
        }

        SelectionRequest request(int pages) {
            return new SelectionRequest(List.copyOf(sections), pages, CAPACITY);
        }

        SelectionRequest request(CapacityModel capacity) {
            return new SelectionRequest(List.copyOf(sections), 1, capacity);
        }

        /**
         * What is on the page, which is not the same list as
         * {@code selected()}. Bolum 20.2's degree line reaches the page as a
         * header-only entry and never as an atom, so a section made only of
         * those — Education, always — is printed and absent from
         * {@code selected()}. Reading one and not the other is how this test
         * first reported Education missing from a page it was on.
         */
        List<SectionKind> sectionsOn(SelectionState state) {
            return printedIds(state).stream()
                    .map(kindOfAtom::get)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
        }

        int atomsIn(SelectionState state, SectionKind kind) {
            return (int) printedIds(state).stream()
                    .filter(id -> kind == kindOfAtom.get(id))
                    .count();
        }

        int entriesIn(SelectionState state, SectionKind kind) {
            return (int) printedIds(state).stream()
                    .filter(id -> kind == kindOfAtom.get(id))
                    .map(entryOfAtom::get)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
        }

        private List<UUID> printedIds(SelectionState state) {
            var ids = new ArrayList<UUID>();
            state.selected().forEach(atom -> ids.add(atom.atomId()));
            ids.addAll(state.headerOnlyEntries());
            return ids;
        }
    }
}
