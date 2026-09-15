package com.mustafatetik.atomcv.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What the tree can answer about itself, and the one question a generation
 * asks it before Faz C: can this profile be written in that language out of
 * the wordings it already holds (F-013)?
 *
 * <p>These cases used to live in {@code GenerationOptionsTest}, because the
 * answer was what chose the document's language. It no longer is — Bolum
 * 21.8's second step translates what is missing and only a translation that
 * could not be made falls the document back — so the question belongs to the
 * tree rather than to the options, and it is still asked: a true answer is a
 * generation that makes no calls at all.
 */
class ProfileTreeTest {

    private static final UUID PROFILE = UUID.randomUUID();

    @Test
    void aprofileWithEveryWordingCanBeWrittenInThatLanguage() {
        assertThat(writtenIn("tr", "en").canBeWrittenIn("en")).isTrue();
    }

    /** One untranslated atom is enough: a CV in two languages is the defect. */
    @Test
    void onemissingWordingIsEnoughToSayNo() {
        assertThat(partlyTranslated().canBeWrittenIn("en")).isFalse();
    }

    @Test
    void aprofileWithNoWordingAtAllInThatLanguageSaysNo() {
        assertThat(writtenIn("tr").canBeWrittenIn("en")).isFalse();
    }

    /**
     * An atom the user switched off never reaches the page, so it has no say
     * in what language the page comes out in — and, now, no say in whether a
     * generation pays for a translation.
     */
    @Test
    void aninactiveAtomIsNotAsked() {
        assertThat(withInactiveTurkishOnly().canBeWrittenIn("en")).isTrue();
    }

    /** No language is not a language a document can be written in. */
    @Test
    void nothingAskedIsNo() {
        assertThat(writtenIn("tr", "en").canBeWrittenIn(null)).isFalse();
        assertThat(writtenIn("tr", "en").canBeWrittenIn("  ")).isFalse();
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    /** Two atoms, each carrying a wording in every language named. */
    private static ProfileTree writtenIn(String... languages) {
        var section = new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 0);
        var first = new Atom(PROFILE, section.getId(), null, AtomKind.BULLET, (short) 0);
        var second = new Atom(PROFILE, section.getId(), null, AtomKind.BULLET, (short) 1);

        var variants = new ArrayList<AtomVariant>();
        for (Atom atom : List.of(first, second)) {
            for (String language : languages) {
                variants.add(wording(atom, language, languages[0].equals(language)));
            }
        }
        return ProfileAssembler.assemble(PROFILE, List.of(section), List.of(),
                List.of(first, second), variants);
    }

    /** The real shape of F-013: most atoms translated, one not. */
    private static ProfileTree partlyTranslated() {
        var section = new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 0);
        var translated = new Atom(PROFILE, section.getId(), null, AtomKind.BULLET, (short) 0);
        var left = new Atom(PROFILE, section.getId(), null, AtomKind.BULLET, (short) 1);

        return ProfileAssembler.assemble(PROFILE, List.of(section), List.of(),
                List.of(translated, left),
                List.of(wording(translated, "tr", true), wording(translated, "en", false),
                        wording(left, "tr", true)));
    }

    private static ProfileTree withInactiveTurkishOnly() {
        var section = new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 0);
        var active = new Atom(PROFILE, section.getId(), null, AtomKind.BULLET, (short) 0);
        var off = new Atom(PROFILE, section.getId(), null, AtomKind.BULLET, (short) 1);
        off.setActive(false);

        return ProfileAssembler.assemble(PROFILE, List.of(section), List.of(),
                List.of(active, off),
                List.of(wording(active, "tr", true), wording(active, "en", false),
                        wording(off, "tr", true)));
    }

    private static AtomVariant wording(Atom atom, String language, boolean primary) {
        var variant = new AtomVariant(PROFILE, atom.getId(), language,
                RichContent.plain(language + " wording"));
        variant.setPrimary(primary);
        return variant;
    }
}
