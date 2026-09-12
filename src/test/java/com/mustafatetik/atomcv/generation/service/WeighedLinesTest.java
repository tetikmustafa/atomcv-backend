package com.mustafatetik.atomcv.generation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The list both halves of Faz G read (Bolum 24.2, 24.4, F-031).
 *
 * <p>These cases were the sentence endpoint's and had no test of their own:
 * the numbering was checked, the text behind the numbers was not. They are
 * here now because the selection endpoint draws its toggles from the same
 * answer, and a list that put one sentence in front of a model and another in
 * front of a person would make the two disagree about line four.
 */
class WeighedLinesTest {

    private static final UUID PROFILE = UUID.randomUUID();

    /** The order a person is looking at: the page, then what did not fit. */
    @Test
    void thepageComesFirstAndWhatDidNotFitFollowsIt() {
        Atom printed = atomRow();
        Atom held = atomRow();
        ProfileTree tree = treeOf(
                node(printed, "Led the migration"),
                node(held, "Wrote the deploy script"));

        List<WeighedLines.Line> lines = WeighedLines.of(
                tree, snapshot(List.of(selected(printed)), List.of(rejected(held, 0.4))),
                RewrittenContent.none(), Tone.FORMAL);

        assertThat(lines).extracting(WeighedLines.Line::text)
                .containsExactly("Led the migration", "Wrote the deploy script");
        assertThat(lines).extracting(WeighedLines.Line::onPage)
                .containsExactly(true, false);
    }

    /**
     * Ranked by the score they competed on, so what is offered first is what
     * came closest to the page.
     */
    @Test
    void whatDidNotFitIsRankedByTheScoreItCompetedOn() {
        Atom nearly = atomRow();
        Atom nowhere = atomRow();
        ProfileTree tree = treeOf(node(nearly, "Nearly"), node(nowhere, "Nowhere"));

        List<WeighedLines.Line> lines = WeighedLines.of(
                tree,
                snapshot(List.of(), List.of(rejected(nowhere, 0.1), rejected(nearly, 0.9))),
                RewrittenContent.none(), Tone.FORMAL);

        assertThat(lines).extracting(WeighedLines.Line::text)
                .containsExactly("Nearly", "Nowhere");
    }

    /**
     * Two reads of one generation produce one order. A list that moved between
     * them would renumber a model's prompt (Bolum 53.3) and slide a toggle
     * under somebody's cursor.
     */
    @Test
    void tiedScoresAreBrokenTheSameWayEveryTime() {
        Atom first = atomRow();
        Atom second = atomRow();
        ProfileTree tree = treeOf(node(first, "One"), node(second, "Two"));

        var forwards = WeighedLines.of(tree,
                snapshot(List.of(), List.of(rejected(first, 0.5), rejected(second, 0.5))),
                RewrittenContent.none(), Tone.FORMAL);
        var backwards = WeighedLines.of(tree,
                snapshot(List.of(), List.of(rejected(second, 0.5), rejected(first, 0.5))),
                RewrittenContent.none(), Tone.FORMAL);

        assertThat(forwards).isEqualTo(backwards);
    }

    /**
     * What the CV printed, not what the profile says today: Faz D rewrote this
     * line, and the person is looking at the rewrite (EK D.6.3).
     */
    @Test
    void apageLineCarriesTheWordingFazDWroteForIt() {
        Atom printed = atomRow();
        ProfileTree tree = treeOf(node(printed, "Led the migration"));
        var rewritten = new RewrittenContent(
                Map.of(printed.getId(), RichContent.plain("Led a zero-downtime migration")));

        List<WeighedLines.Line> lines = WeighedLines.of(
                tree, snapshot(List.of(selected(printed)), List.of()), rewritten, Tone.FORMAL);

        assertThat(lines).extracting(WeighedLines.Line::text)
                .containsExactly("Led a zero-downtime migration");
    }

    /**
     * A held-back line does not, and that is not an oversight: this generation
     * never printed one for it, and putting it back runs Faz D over it afresh.
     */
    @Test
    void aheldBackLineCarriesTheProfilesOwnWording() {
        Atom held = atomRow();
        ProfileTree tree = treeOf(node(held, "Wrote the deploy script"));
        var rewritten = new RewrittenContent(
                Map.of(held.getId(), RichContent.plain("Automated the deploy")));

        List<WeighedLines.Line> lines = WeighedLines.of(
                tree, snapshot(List.of(), List.of(rejected(held, 0.4))), rewritten, Tone.FORMAL);

        assertThat(lines).extracting(WeighedLines.Line::text)
                .containsExactly("Wrote the deploy script");
    }

    /** The wording the selection costed, not whichever the picker prefers today. */
    @Test
    void apageLineCarriesTheVariantTheSnapshotNamed() {
        Atom printed = atomRow();
        AtomVariant primary = wording(printed, "en", "Led the migration", true);
        AtomVariant costed = wording(printed, "en", "Cut over the cluster in place", false);
        ProfileTree tree = treeOf(new ProfileTree.AtomNode(printed, List.of(primary, costed)));

        List<WeighedLines.Line> lines = WeighedLines.of(tree,
                snapshot(List.of(new SelectionState.SelectedAtom(
                                printed.getId(), costed.getId(), 0.8, 12.0, false)),
                        List.of()),
                RewrittenContent.none(), Tone.FORMAL);

        assertThat(lines).extracting(WeighedLines.Line::text)
                .containsExactly("Cut over the cluster in place");
    }

    /**
     * An atom deleted from the profile since is absent rather than listed. It
     * cannot be put back, and asking to drop it is already true, so a line for
     * it would be one nobody can act on.
     */
    @Test
    void anatomDeletedSinceIsNotOffered() {
        Atom gone = atomRow();
        Atom stayed = atomRow();
        ProfileTree tree = treeOf(node(stayed, "Still here"));

        List<WeighedLines.Line> lines = WeighedLines.of(tree,
                snapshot(List.of(selected(stayed)), List.of(rejected(gone, 0.4))),
                RewrittenContent.none(), Tone.FORMAL);

        assertThat(lines).extracting(WeighedLines.Line::atomId)
                .containsExactly(stayed.getId());
    }

    /** An atom with no wording at all has nothing to show and is left out. */
    @Test
    void anatomWithNoWordingIsNotOffered() {
        Atom wordless = atomRow();
        ProfileTree tree = treeOf(new ProfileTree.AtomNode(wordless, List.of()));

        assertThat(WeighedLines.of(tree,
                snapshot(List.of(selected(wordless)), List.of()),
                RewrittenContent.none(), Tone.FORMAL)).isEmpty();
    }

    private static StoredSelection snapshot(
            List<SelectionState.SelectedAtom> selected,
            List<SelectionState.RejectedAtom> rejected) {

        return new StoredSelection("en", null,
                new SelectionState.BudgetBreakdown(700.0, 100.0, 600.0, 120.0),
                selected, rejected);
    }

    private static SelectionState.SelectedAtom selected(Atom atom) {
        return new SelectionState.SelectedAtom(atom.getId(), null, 0.8, 12.0, false);
    }

    private static SelectionState.RejectedAtom rejected(Atom atom, double score) {
        return new SelectionState.RejectedAtom(
                atom.getId(), score, SelectionState.RejectionReason.BUDGET);
    }

    private static ProfileTree treeOf(ProfileTree.AtomNode... atoms) {
        var section = new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 0);
        return new ProfileTree(PROFILE, List.of(
                new ProfileTree.SectionNode(section, List.of(), List.of(atoms))));
    }

    private static ProfileTree.AtomNode node(Atom atom, String text) {
        return new ProfileTree.AtomNode(atom, List.of(wording(atom, "en", text, true)));
    }

    private static Atom atomRow() {
        return new Atom(PROFILE, UUID.randomUUID(), UUID.randomUUID(),
                AtomKind.BULLET, (short) 0);
    }

    private static AtomVariant wording(
            Atom atom, String language, String text, boolean primary) {

        AtomVariant variant =
                new AtomVariant(PROFILE, atom.getId(), language, RichContent.plain(text));
        variant.setPrimary(primary);
        return variant;
    }
}
