package com.mustafatetik.atomcv.generation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What {@code auto} means, which is not the same thing in the two modes
 * (Bolum 14.4), and which language a profile can actually be written in
 * (F-013).
 *
 * <p>The preference reads "follow the posting". General mode has no posting to
 * follow and falls back to the language the profile was written in; job mode
 * does — but only follows it as far as the profile's own wordings reach.
 */
class GenerationOptionsTest {

    private static final UUID PROFILE = UUID.randomUUID();

    @Test
    void autofollowsThePostingWhenThereIsOne() {
        var profile = profileWritten("en");

        assertThat(GenerationOptions.forPosting(profile, writtenIn("en", "tr"), "tr").language())
                .isEqualTo("tr");
    }

    /**
     * The extraction does not always name a language. Falling back to the
     * profile's own is the general-mode answer, and it beats rendering a CV in
     * a language nobody asked for.
     */
    @Test
    void apostingThatNamedNoLanguageFallsBackToTheProfiles() {
        var profile = profileWritten("tr");
        var tree = writtenIn("tr", "en");

        assertThat(GenerationOptions.forPosting(profile, tree, "").language()).isEqualTo("tr");
        assertThat(GenerationOptions.forPosting(profile, tree, null).language()).isEqualTo("tr");
    }

    /** A preference that names a language is a decision, not a default. */
    @Test
    void anexplicitPreferenceOutranksThePosting() {
        var profile = profileWritten("tr");
        profile.setPreferences(new Preferences(Preferences.WritingStyle.DEFAULTS,
                new Preferences.Defaults(1, "classic", "en", "auto")));

        assertThat(GenerationOptions.forPosting(profile, writtenIn("tr"), "de").language())
                .isEqualTo("en");
    }

    /** And the request outranks both, the same way it does in general mode. */
    @Test
    void therequestStillWins() {
        assertThat(GenerationOptions.forPosting(profileWritten("en"), writtenIn("en"), "tr")
                .withLanguage("de").language()).isEqualTo("de");
    }

    /** General mode has nothing to follow, and says so by not changing. */
    @Test
    void generalmodeIsUnaffected() {
        var profile = profileWritten("tr");

        assertThat(GenerationOptions.defaultsOf(profile).language()).isEqualTo("tr");
    }

    /**
     * F-013. Before this, the posting's language won unconditionally and
     * selection quietly fell back to the primary wording for every atom that
     * had no translation — while the dates kept following the posting. The
     * result was Turkish bullets under English dates.
     */
    @Test
    void apostingLanguageTheProfileCannotBeWrittenInIsNotFollowed() {
        var profile = profileWritten("tr");

        assertThat(GenerationOptions.forPosting(profile, writtenIn("tr"), "en").language())
                .isEqualTo("tr");
    }

    /** One untranslated atom is enough: a CV in two languages is the defect. */
    @Test
    void onemissingWordingIsEnoughToKeepTheProfilesLanguage() {
        var profile = profileWritten("tr");

        assertThat(GenerationOptions.forPosting(profile, partlyTranslated(), "en").language())
                .isEqualTo("tr");
    }

    /**
     * The half of Bolum 21.8 that does work — a wording that already exists in
     * the target language costs nothing — is not given up to fix the other
     * half.
     */
    @Test
    void afullyTranslatedProfileStillFollowsThePosting() {
        var profile = profileWritten("tr");

        assertThat(GenerationOptions.forPosting(profile, writtenIn("tr", "en"), "en").language())
                .isEqualTo("en");
    }

    /**
     * An atom the user switched off never reaches the page, so it has no say
     * in what language the page comes out in.
     */
    @Test
    void aninactiveAtomDoesNotDecideTheLanguage() {
        var profile = profileWritten("tr");

        assertThat(GenerationOptions.forPosting(profile, withInactiveTurkishOnly(), "en")
                .language()).isEqualTo("en");
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private static Profile profileWritten(String sourceLanguage) {
        var profile = new Profile(UUID.randomUUID());
        profile.setSourceLanguage(sourceLanguage);
        return profile;
    }

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
