package com.mustafatetik.atomcv.generation.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bolum 21.1 — the cheapest rewrite is the one somebody already wrote.
 *
 * <p>These cases used to live next to the planner, and they moved with the
 * code: the choice is made in front of the budget now, because selection
 * charges for the wording it costed and Faz D must rewrite that same one.
 */
class AlternativeWordingTest {

    private static final UUID PROFILE = UUID.randomUUID();

    /**
     * The investment somebody made in the profile editor, paying off: two
     * wordings, and the one in the tone this profile asked for is printed. No
     * model was called to get it.
     */
    @Test
    void thewordingInTheRequestedToneIsTheOneChosen() {
        Atom row = atomRow();
        AtomVariant formal = wording(row, "en", "Led the migration", true);
        AtomVariant technical = wording(row, "en", "Cut over the cluster in place", false);
        technical.setTone(Tone.TECHNICAL);

        var chosen = AlternativeWording.pick(
                new AtomNode(row, List.of(formal, technical)), "en", Tone.TECHNICAL);

        assertThat(chosen).contains(technical);
    }

    /** No wording in that tone is not a reason to print nothing (Bolum 21.8). */
    @Test
    void anatomWithNoWordingInThatToneKeepsTheOneItHas() {
        Atom row = atomRow();
        AtomVariant only = wording(row, "en", "Led the migration", true);

        var chosen = AlternativeWording.pick(new AtomNode(row, List.of(only)), "en", Tone.CASUAL);

        assertThat(chosen).contains(only);
    }

    /** And the language comes first: a CV in the wrong one is not a style question. */
    @Test
    void thelanguageIsChosenBeforeTheTone() {
        Atom row = atomRow();
        AtomVariant turkish = wording(row, "tr", "Gecisi yonettim", true);
        AtomVariant english = wording(row, "en", "Led the migration", false);
        english.setTone(Tone.FORMAL);

        var chosen = AlternativeWording.pick(
                new AtomNode(row, List.of(turkish, english)), "tr", Tone.FORMAL);

        assertThat(chosen).contains(turkish);
    }

    /**
     * An atom with wordings but none the person marked primary still prints.
     * The predecessor of this code fell back to {@code primaryVariant()} and
     * dropped the atom when there was none — an import that set no primary
     * flag would have silently lost bullets.
     */
    @Test
    void anatomWithNoPrimaryWordingIsStillPrinted() {
        Atom row = atomRow();
        AtomVariant only = wording(row, "de", "Migration geleitet", false);

        var chosen = AlternativeWording.pick(new AtomNode(row, List.of(only)), "en", Tone.FORMAL);

        assertThat(chosen).contains(only);
    }

    @Test
    void anatomWithNoWordingAtAllHasNothingToChoose() {
        assertThat(AlternativeWording.pick(
                new AtomNode(atomRow(), List.of()), "en", Tone.FORMAL)).isEmpty();
    }

    /** Two runs of one generation must produce the same CV (design principle 2). */
    @Test
    void twowordingsThatTieAreBrokenTheSameWayEveryTime() {
        Atom row = atomRow();
        AtomVariant first = wording(row, "en", "Led the migration", false);
        AtomVariant second = wording(row, "en", "Ran the migration", false);
        var forwards = new AtomNode(row, List.of(first, second));
        var backwards = new AtomNode(row, List.of(second, first));

        assertThat(AlternativeWording.pick(forwards, "en", Tone.FORMAL))
                .isEqualTo(AlternativeWording.pick(backwards, "en", Tone.FORMAL));
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
