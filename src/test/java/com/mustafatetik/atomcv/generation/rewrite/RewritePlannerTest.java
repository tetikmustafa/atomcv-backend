package com.mustafatetik.atomcv.generation.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.EntryNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.SectionNode;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bolum 21.1-21.3, which is the whole of Faz D that costs nothing.
 *
 * <p>Every case here is a promise about what the product will not do to
 * somebody's sentences: not touch the ones with no real connection to the
 * posting, not touch the ones they marked as fixed, not rewrite the whole CV,
 * and not come back longer than it went in.
 */
class RewritePlannerTest {

    private static final UUID PROFILE = UUID.randomUUID();

    // -- Bolum 21.2, the three tiers ---------------------------------------

    @Test
    void astrongMatchIsAdapted() {
        var atom = bullet("Moved 300K rows with Microsoft Fabric", List.of("microsoft-fabric"));

        var plan = planFor(List.of(scored(atom, 0.80)), atom);

        assertThat(plan.candidates()).singleElement()
                .extracting(RewriteCandidate::intent).isEqualTo(RewriteIntent.ADAPT);
    }

    /**
     * The floor, and the sentence Bolum 21.2 gives for it: with no connection,
     * adapting is not adaptation. This is the case where the product's answer
     * to "make my CV fit" is no.
     */
    @Test
    void anatomWithNoConnectionToThePostingIsLeftAlone() {
        var atom = bullet("Ran the office football team", List.of());

        var plan = planFor(List.of(scored(atom, 0.39)), atom);

        assertThat(plan.candidates()).isEmpty();
        // Still printed, though — not being rewritten is not being dropped.
        assertThat(plan.wordings()).containsKey(atom.atom().getId());
    }

    /**
     * <strong>And length is not a way past the floor.</strong> The obvious
     * shape of this code is "adapt if it scores, otherwise compress if it is
     * long", which reads correctly and quietly compresses bullets that have
     * nothing to do with the posting. Bolum 21.2's third tier is
     * <em>do not touch</em>, and it is a floor rather than a preference: an
     * unrelated sentence is not improved by being made shorter, it is just
     * altered without a reason.
     */
    @Test
    void alongAtomBelowTheFloorIsStillLeftAlone() {
        var atom = bullet("Ran the office football team for four seasons, organising the "
                + "fixture list, collecting subscriptions, booking the pitch every Thursday "
                + "and washing the kit when nobody else volunteered to do it",
                List.of());

        var plan = planFor(List.of(scored(atom, 0.39)), atom);

        assertThat(plan.candidates()).isEmpty();
    }

    /**
     * The middle tier is compression and only compression, and only when there
     * is something to compress. A short bullet that scored 0.5 is not the
     * problem with anybody's CV.
     */
    @Test
    void amiddlingMatchIsCompressedOnlyWhenItIsLong() {
        var shortOne = bullet("Cut the nightly batch to forty minutes", List.of("etl"));
        var longOne = bullet("Rebuilt the nightly batch pipeline end to end, moving it off "
                + "the shared scheduler and onto its own queue, which cut the run from four "
                + "hours to forty minutes and stopped the morning reports arriving late",
                List.of("etl"));

        var plan = planFor(List.of(scored(shortOne, 0.50), scored(longOne, 0.50)),
                shortOne, longOne);

        assertThat(plan.candidates()).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.atomId()).isEqualTo(longOne.atom().getId());
                    assertThat(candidate.intent()).isEqualTo(RewriteIntent.COMPRESS);
                });
    }

    // -- the two exclusions before the tiers -------------------------------

    /**
     * The person said this sentence is to be printed as written. A rewrite
     * would be the product overruling them, and the score is beside the point.
     */
    @Test
    void averbatimAtomIsNeverSentHoweverWellItScores() {
        var atom = bullet("Patent US 9,384,203", List.of("patents"));
        atom.atom().setVerbatim(true);

        var plan = planFor(List.of(scored(atom, 0.95)), atom);

        assertThat(plan.candidates()).isEmpty();
        assertThat(plan.wordings()).containsKey(atom.atom().getId());
    }

    @Test
    void anatomWithNoWordingAtAllIsNeitherPrintedNorRewritten() {
        var atom = new AtomNode(atomRow(List.of()), List.of());

        var plan = planFor(List.of(scored(atom, 0.90)), atom);

        assertThat(plan.candidates()).isEmpty();
        assertThat(plan.wordings()).isEmpty();
    }

    // -- Bolum 21.2's cap --------------------------------------------------

    /**
     * Ten strong matches, eight rewrites. The cap is a cost ceiling and a
     * guard against the CV where every sentence has been stuffed with the
     * posting's vocabulary.
     */
    @Test
    void atmostEightAtomsAreRewrittenAndTheyAreTheBestEight() {
        List<AtomNode> atoms = new ArrayList<>();
        List<SelectionState.SelectedAtom> selected = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            var atom = bullet("Bullet number " + i, List.of("java"));
            atoms.add(atom);
            selected.add(scored(atom, 0.70 + i * 0.01));
        }

        var plan = planFor(selected, atoms.toArray(new AtomNode[0]));

        assertThat(plan.candidates()).hasSize(RewritePlanner.MAX_CANDIDATES);
        assertThat(plan.candidates().get(0).score()).isEqualTo(0.79, within(1e-9));
        assertThat(plan.candidates()).extracting(RewriteCandidate::score)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    /** Two runs of one generation must produce the same CV (design principle 2). */
    @Test
    void atieAtTheCapIsBrokenTheSameWayEveryTime() {
        List<AtomNode> atoms = new ArrayList<>();
        List<SelectionState.SelectedAtom> selected = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            var atom = bullet("Bullet number " + i, List.of("java"));
            atoms.add(atom);
            selected.add(scored(atom, 0.70));
        }
        var array = atoms.toArray(new AtomNode[0]);

        var first = planFor(selected, array);
        var second = planFor(selected, array);

        assertThat(first.candidates()).extracting(RewriteCandidate::atomId)
                .isEqualTo(second.candidates().stream().map(RewriteCandidate::atomId).toList());
    }

    // -- Bolum 21.3 --------------------------------------------------------

    /**
     * Faz C chose these atoms by their measured cost. A rewrite that came back
     * longer would spend a page the selection had already promised away — and
     * the page limit is the product's one mathematical guarantee.
     */
    @Test
    void therewriteMayBeFivePerCentLongerAndNoMore() {
        var atom = bullet("A sentence of exactly forty characters!!", List.of("java"));

        var plan = planFor(List.of(scored(atom, 0.90)), atom);

        assertThat(plan.candidates()).singleElement()
                .extracting(RewriteCandidate::maxChars).isEqualTo(42);
    }

    // -- Bolum 21.4's honesty constraint -----------------------------------

    /**
     * The atom's own skills travel with it, because they are the boundary of
     * what it may claim. A candidate that arrived without them would be a
     * request to write whatever sounds good.
     */
    @Test
    void acandidateCarriesWhatTheSentenceIsAllowedToClaim() {
        var atom = bullet("Moved 300K rows with Microsoft Fabric",
                List.of("microsoft-fabric"), List.of("300K"), List.of("Microsoft Fabric"));

        var plan = planFor(List.of(scored(atom, 0.90)), atom);

        assertThat(plan.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.skills()).containsExactly("microsoft-fabric");
            assertThat(candidate.metrics()).containsExactly("300K");
            assertThat(candidate.properNouns()).containsExactly("Microsoft Fabric");
        });
    }

    // -- Bolum 21.1 --------------------------------------------------------

    /**
     * The investment somebody made in the profile editor, paying off: two
     * wordings, and the one in the tone this profile asked for is printed. No
     * model was called to get it.
     */
    @Test
    void thewordingInTheRequestedToneIsTheOneChosen() {
        Atom row = atomRow(List.of("java"));
        AtomVariant formal = wording(row, "en", RichContent.plain("Led the migration"), true);
        AtomVariant technical = wording(row, "en",
                RichContent.plain("Cut over the cluster in place"), false);
        technical.setTone(Tone.TECHNICAL);
        var atom = new AtomNode(row, List.of(formal, technical));

        var plan = plan(tree(atom), selection(List.of(scored(atom, 0.10))), "en", Tone.TECHNICAL);

        assertThat(plan.wordings()).containsEntry(row.getId(), technical.getId());
    }

    /** No wording in that tone is not a reason to print nothing (Bolum 21.8). */
    @Test
    void anatomWithNoWordingInThatToneKeepsTheOneItHas() {
        var atom = bullet("Led the migration", List.of("java"));

        var plan = plan(tree(atom), selection(List.of(scored(atom, 0.10))), "en", Tone.CASUAL);

        assertThat(plan.wordings()).containsEntry(
                atom.atom().getId(), atom.variants().get(0).getId());
    }

    /** And the language comes first: a CV in the wrong one is not a style question. */
    @Test
    void thelanguageIsChosenBeforeTheTone() {
        Atom row = atomRow(List.of("java"));
        AtomVariant turkish = wording(row, "tr", RichContent.plain("Gecisi yonettim"), true);
        AtomVariant english = wording(row, "en", RichContent.plain("Led the migration"), false);
        english.setTone(Tone.FORMAL);
        var atom = new AtomNode(row, List.of(turkish, english));

        var plan = plan(tree(atom), selection(List.of(scored(atom, 0.10))), "tr", Tone.FORMAL);

        assertThat(plan.wordings()).containsEntry(row.getId(), turkish.getId());
    }

    // -- fixtures ----------------------------------------------------------

    private static RewritePlan planFor(
            List<SelectionState.SelectedAtom> selected, AtomNode... atoms) {
        return plan(tree(atoms), selection(selected), "en", Tone.FORMAL);
    }

    private static RewritePlan plan(
            ProfileTree tree, SelectionState selection, String language, Tone tone) {
        return RewritePlanner.plan(tree, selection, language, tone);
    }

    private static SelectionState selection(List<SelectionState.SelectedAtom> selected) {
        return new SelectionState(selected, List.of(),
                new SelectionState.BudgetBreakdown(600, 100, 500, 300));
    }

    private static SelectionState.SelectedAtom scored(AtomNode atom, double score) {
        UUID variantId = atom.variants().isEmpty()
                ? UUID.randomUUID() : atom.variants().get(0).getId();
        return new SelectionState.SelectedAtom(
                atom.atom().getId(), variantId, score, 12.0, false);
    }

    private static ProfileTree tree(AtomNode... atoms) {
        Section section = new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 0);
        Entry entry = new Entry(PROFILE, section.getId(), "Data Engineer", (short) 0);
        var entryNode = new EntryNode(entry, List.of(atoms));
        return new ProfileTree(PROFILE,
                List.of(new SectionNode(section, List.of(entryNode), List.of())));
    }

    private static AtomNode bullet(String text, List<String> skills) {
        return bullet(text, skills, List.of(), List.of());
    }

    private static AtomNode bullet(String text, List<String> skills,
            List<String> metrics, List<String> properNouns) {
        Atom row = atomRow(skills);
        row.setMetrics(metrics);
        row.setProperNouns(properNouns);
        return new AtomNode(row, List.of(wording(row, "en", RichContent.plain(text), true)));
    }

    private static Atom atomRow(List<String> skills) {
        Atom atom = new Atom(PROFILE, UUID.randomUUID(), UUID.randomUUID(),
                AtomKind.BULLET, (short) 0);
        atom.setSkills(skills);
        return atom;
    }

    private static AtomVariant wording(
            Atom atom, String language, RichContent content, boolean primary) {
        AtomVariant variant = new AtomVariant(PROFILE, atom.getId(), language, content);
        variant.setPrimary(primary);
        return variant;
    }
}
