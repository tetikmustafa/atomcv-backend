package com.mustafatetik.atomcv.generation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What {@code auto} means, which is not the same thing in the two modes.
 *
 * <p>The preference reads "follow the posting". General mode has no posting to
 * follow and falls back to the language the profile was written in; job mode
 * follows it, and does so whether or not the profile has been translated — the
 * second step is what makes that safe, and {@code ProfileTreeTest} keeps the
 * question it asks. An explicit preference and an explicit request still
 * outrank the posting, in that order.
 */
class GenerationOptionsTest {

    private static final UUID PROFILE = UUID.randomUUID();

    @Test
    void autofollowsThePostingWhenThereIsOne() {
        var profile = profileWritten("en");

        assertThat(GenerationOptions.forPosting(profile, "tr").language()).isEqualTo("tr");
    }

    /**
     * The extraction does not always name a language. Falling back to the
     * profile's own is the general-mode answer, and it beats rendering a CV in
     * a language nobody asked for.
     */
    @Test
    void apostingThatNamedNoLanguageFallsBackToTheProfiles() {
        var profile = profileWritten("tr");

        assertThat(GenerationOptions.forPosting(profile, "").language()).isEqualTo("tr");
        assertThat(GenerationOptions.forPosting(profile, null).language()).isEqualTo("tr");
    }

    // -- the template the profile asked for ---------------------

    /**
     * <strong>The preference has carried a templateId from the start and this
     * ignored it.</strong> Every CV came out classic whatever the profile
     * said, and nothing failed, because there was only one template to be
     * wrong about.
     */
    @Test
    void thetemplateThePreferenceNamesIsTheOneThatIsUsed() {
        var profile = profileWritten("en");
        profile.setPreferences(new Preferences(Preferences.WritingStyle.DEFAULTS,
                new Preferences.Defaults(1, "compact", "en", "auto")));

        assertThat(GenerationOptions.defaultsOf(profile).customization())
                .isEqualTo(TemplateCustomization.COMPACT);
        assertThat(GenerationOptions.forPosting(profile, "en").customization())
                .isEqualTo(TemplateCustomization.COMPACT);
    }

    @Test
    void aprofileThatNamesNoTemplateGetsClassic() {
        var profile = profileWritten("en");
        profile.setPreferences(new Preferences(Preferences.WritingStyle.DEFAULTS,
                new Preferences.Defaults(1, null, "en", "auto")));

        assertThat(GenerationOptions.defaultsOf(profile).customization())
                .isEqualTo(TemplateCustomization.CLASSIC);
    }

    /**
     * A template that has since been withdrawn produces a CV in the default
     * rather than no CV: the id is a stored preference and the person did not
     * do anything wrong.
     */
    @Test
    void anunknownTemplateFallsBackRatherThanFailing() {
        var profile = profileWritten("en");
        profile.setPreferences(new Preferences(Preferences.WritingStyle.DEFAULTS,
                new Preferences.Defaults(1, "art-deco", "en", "auto")));

        assertThat(GenerationOptions.defaultsOf(profile).customization())
                .isEqualTo(TemplateCustomization.CLASSIC);
    }

    // -- the sliders the profile moved --------------------------

    @Test
    void themovedSlidersReachTheDocument() {
        var profile = profileWritten("en");
        profile.setPreferences(new Preferences(Preferences.WritingStyle.DEFAULTS,
                new Preferences.Defaults(1, "classic", "en", "auto",
                        new Preferences.Appearance(9.5, 0.65, 1.15, "SANS", "1D4ED8"))));

        var chosen = GenerationOptions.defaultsOf(profile).customization();

        assertThat(chosen.fontSizePt()).isEqualTo(9.5);
        assertThat(chosen.marginInches()).isEqualTo(0.65);
        assertThat(chosen.lineSpacing()).isEqualTo(1.15);
        assertThat(chosen.fontFamily()).isEqualTo(FontFamily.SANS);
        assertThat(chosen.accentColor().value()).isEqualTo("1D4ED8");
        assertThat(chosen.baseTemplateId()).isEqualTo("classic");
    }

    /**
     * One slider is one number. A person who changed the font size keeps the
     * template's margin — including a margin the template changes later, which
     * is what somebody who never touched it should get.
     */
    @Test
    void anuntouchedSliderStaysTheTemplates() {
        var profile = profileWritten("en");
        profile.setPreferences(new Preferences(Preferences.WritingStyle.DEFAULTS,
                new Preferences.Defaults(1, "compact", "en", "auto",
                        new Preferences.Appearance(11.0, null, null, null, null))));

        var chosen = GenerationOptions.defaultsOf(profile).customization();

        assertThat(chosen.fontSizePt()).isEqualTo(11.0);
        assertThat(chosen.marginInches())
                .isEqualTo(TemplateCustomization.COMPACT.marginInches());
        assertThat(chosen.lineSpacing())
                .isEqualTo(TemplateCustomization.COMPACT.lineSpacing());
    }

    /** A row written before the sliders existed is a template at its own settings. */
    @Test
    void aprofileWithNoAppearanceIsTheTemplateItself() {
        var profile = profileWritten("en");
        profile.setPreferences(new Preferences(Preferences.WritingStyle.DEFAULTS,
                new Preferences.Defaults(1, "classic", "en", "auto")));

        assertThat(GenerationOptions.defaultsOf(profile).customization())
                .isEqualTo(TemplateCustomization.CLASSIC);
    }

    /**
     * <strong>An unusable stored value falls back rather than failing.</strong>
     * The ranges live on TemplateCustomization and a preference outside them --
     * written before a range moved, or by a release that validated differently
     * -- would otherwise throw and take the CV with it. A document in the
     * template's own settings is a worse answer than the one asked for and a
     * much better one than no document.
     */
    @Test
    void anunusableStoredValueFallsBackToTheTemplate() {
        var profile = profileWritten("en");
        profile.setPreferences(new Preferences(Preferences.WritingStyle.DEFAULTS,
                new Preferences.Defaults(1, "classic", "en", "auto",
                        new Preferences.Appearance(4.0, null, null, null, null))));

        assertThat(GenerationOptions.defaultsOf(profile).customization())
                .isEqualTo(TemplateCustomization.CLASSIC);
    }

    @Test
    void anunknownFontFamilyFallsBackToo() {
        var profile = profileWritten("en");
        profile.setPreferences(new Preferences(Preferences.WritingStyle.DEFAULTS,
                new Preferences.Defaults(1, "classic", "en", "auto",
                        new Preferences.Appearance(null, null, null, "COMIC", null))));

        assertThat(GenerationOptions.defaultsOf(profile).customization())
                .isEqualTo(TemplateCustomization.CLASSIC);
    }

    /**
     * And a moved slider is a different document, so it is filed under a
     * different key -- the thing #173 exists for.
     */
    @Test
    void amovedSliderIsAdifferentCostKey() {
        var profile = profileWritten("en");
        profile.setPreferences(new Preferences(Preferences.WritingStyle.DEFAULTS,
                new Preferences.Defaults(1, "classic", "en", "auto",
                        new Preferences.Appearance(9.5, null, null, null, null))));

        assertThat(GenerationOptions.defaultsOf(profile).customization().costKey())
                .isNotEqualTo(TemplateCustomization.CLASSIC.costKey());
    }

    /** A preference that names a language is a decision, not a default. */
    @Test
    void anexplicitPreferenceOutranksThePosting() {
        var profile = profileWritten("tr");
        profile.setPreferences(new Preferences(Preferences.WritingStyle.DEFAULTS,
                new Preferences.Defaults(1, "classic", "en", "auto")));

        assertThat(GenerationOptions.forPosting(profile, "de").language()).isEqualTo("en");
    }

    /** And the request outranks both, the same way it does in general mode. */
    @Test
    void therequestStillWins() {
        assertThat(GenerationOptions.forPosting(profileWritten("en"), "tr")
                .withLanguage("de").language()).isEqualTo("de");
    }

    /** General mode has nothing to follow, and says so by not changing. */
    @Test
    void generalmodeIsUnaffected() {
        var profile = profileWritten("tr");

        assertThat(GenerationOptions.defaultsOf(profile).language()).isEqualTo("tr");
    }

    /**
     * <strong>F-013, narrowed the way F-013 asked to be narrowed.</strong> An
     * untranslated Turkish profile applying to an English posting is now told
     * "English", and the generation translates what it needs before Faz C
     * ({@code GenerationTranslation}). The options object cannot know whether
     * that will succeed, and the wrong place to guess is here: the gate that
     * used to stand here read the tree and answered "Turkish" for a profile
     * that was one call away from English.
     */
    @Test
    void anuntranslatedProfileStillFollowsThePosting() {
        var profile = profileWritten("tr");

        assertThat(GenerationOptions.forPosting(profile, "en").language()).isEqualTo("en");
    }

    /**
     * And the fallback that replaced the gate is a generation's to make, so it
     * is spelled the way the generation spells it rather than left implicit.
     */
    @Test
    void afailedTranslationIsWrittenAsAlanguageChange() {
        var profile = profileWritten("tr");
        var followed = GenerationOptions.forPosting(profile, "en");

        assertThat(followed.withLanguage(profile.getSourceLanguage()).language())
                .isEqualTo("tr");
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private static Profile profileWritten(String sourceLanguage) {
        var profile = new Profile(UUID.randomUUID());
        profile.setSourceLanguage(sourceLanguage);
        return profile;
    }

    private static AtomVariant wording(Atom atom, String language, boolean primary) {
        var variant = new AtomVariant(PROFILE, atom.getId(), language,
                RichContent.plain(language + " wording"));
        variant.setPrimary(primary);
        return variant;
    }
}
