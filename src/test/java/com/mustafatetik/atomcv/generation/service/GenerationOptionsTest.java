package com.mustafatetik.atomcv.generation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.profile.domain.Profile;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What {@code auto} means, which is not the same thing in the two modes
 * (Bolum 14.4).
 *
 * <p>The preference reads "follow the posting". General mode has no posting to
 * follow and falls back to the language the profile was written in; job mode
 * does, and until now had no way to say so.
 */
class GenerationOptionsTest {

    @Test
    void autofollowsThePostingWhenThereIsOne() {
        assertThat(GenerationOptions.forPosting(profileWritten("en"), "tr").language())
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

        assertThat(GenerationOptions.forPosting(profile, "").language()).isEqualTo("tr");
        assertThat(GenerationOptions.forPosting(profile, null).language()).isEqualTo("tr");
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

    private static Profile profileWritten(String sourceLanguage) {
        var profile = new Profile(UUID.randomUUID());
        profile.setSourceLanguage(sourceLanguage);
        return profile;
    }
}
