package com.mustafatetik.atomcv.profile;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.repository.ProfileRepository;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The profile head: its mapping against the real schema, and the one place a
 * user becomes a {@code ProfileRef}.
 */
class ProfileHeadIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private ProfileResolver resolver;

    private UserContext user;

    @BeforeEach
    void createUser() {
        user = UserContext.of(newUser());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users");
    }

    @Test
    void resolvingCreatesTheProfileOnFirstUse() {
        assertThat(profiles.findOwn(user)).isEmpty();

        var ref = resolver.resolve(user);

        assertThat(profiles.findOwn(user)).isPresent();
        assertThat(ref.id()).isEqualTo(profiles.findOwn(user).orElseThrow().getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profiles WHERE user_id = ?",
                Integer.class, user.userId())).isEqualTo(1);
    }

    @Test
    void resolvingTwiceReturnsTheSameProfile() {
        var first = resolver.resolve(user);
        var second = resolver.resolve(user);

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profiles", Integer.class)).isEqualTo(1);
    }

    @Test
    void twoUsersGetTwoProfiles() {
        var other = UserContext.of(newUser());

        var mine = resolver.resolve(user);
        var theirs = resolver.resolve(other);

        assertThat(theirs.id()).isNotEqualTo(mine.id());
        assertThat(profiles.findById(user, theirs.id()))
                .as("another user's profile reads as absent")
                .isEmpty();
    }

    @Test
    void aNewProfileCarriesTheDocumentedDefaults() {
        var ref = resolver.resolve(user);
        var profile = profiles.findById(user, ref.id()).orElseThrow();

        assertThat(profile.getSourceLanguage()).isEqualTo("en");
        assertThat(profile.getEnabledLanguages()).containsExactly("en");
        assertThat(profile.getCompleteness()).isZero();
        assertThat(profile.getContact().isEmpty()).isTrue();
        assertThat(profile.getPreferences().defaults().maxPages()).isEqualTo(1);
        assertThat(profile.getPreferences().defaults().templateId()).isEqualTo("classic");
        assertThat(profile.getPreferences().writingStyle().tone()).isEqualTo(Tone.FORMAL);
        assertThat(profile.getCreatedAt()).isNotNull();
        assertThat(profile.getUpdatedAt()).isNotNull();
        assertThat(profile.getVersion()).isZero();
    }

    @Test
    void contactAndPreferencesRoundTripThroughJsonb() {
        var ref = resolver.resolve(user);
        var profile = profiles.findById(user, ref.id()).orElseThrow();

        profile.setHeadline("Backend Engineer");
        profile.setContact(new Contact("Mustafa Tetik", "mustafa@example.com", "+90 555 000 00 00",
                "https://linkedin.com/in/example", "https://github.com/example",
                "https://example.com", "İstanbul, Türkiye"));
        profile.setPreferences(new Preferences(
                new Preferences.WritingStyle(true, Tone.TECHNICAL, true, "Lead with leadership"),
                new Preferences.Defaults(2, "modern", "tr", "en")));
        profile.setEnabledLanguages(List.of("en", "tr"));
        profiles.save(user, profile);

        var reloaded = profiles.findById(user, ref.id()).orElseThrow();
        assertThat(reloaded.getContact().name()).isEqualTo("Mustafa Tetik");
        assertThat(reloaded.getContact().location()).isEqualTo("İstanbul, Türkiye");
        assertThat(reloaded.getPreferences().writingStyle().tone()).isEqualTo(Tone.TECHNICAL);
        assertThat(reloaded.getPreferences().writingStyle().customInstructions())
                .isEqualTo("Lead with leadership");
        assertThat(reloaded.getPreferences().defaults().cvLanguage()).isEqualTo("tr");
        assertThat(reloaded.getEnabledLanguages()).containsExactly("en", "tr");

        // Reachable with jsonb operators, and the tone is lowercase in there too.
        assertThat(jdbc.queryForObject(
                "SELECT contact->>'email' FROM profiles WHERE id = ?", String.class, ref.id()))
                .isEqualTo("mustafa@example.com");
        assertThat(jdbc.queryForObject(
                "SELECT preferences->'writingStyle'->>'tone' FROM profiles WHERE id = ?",
                String.class, ref.id()))
                .isEqualTo("technical");
        assertThat(jdbc.queryForObject(
                "SELECT array_length(enabled_languages, 1) FROM profiles WHERE id = ?",
                Integer.class, ref.id()))
                .isEqualTo(2);
    }

    @Test
    void personalDataStaysOutOfToString() {
        var contact = new Contact("Mustafa Tetik", "mustafa@example.com", null, null, null, null,
                "İstanbul");
        var style = new Preferences.WritingStyle(true, Tone.FORMAL, false, "Lead with leadership");

        assertThat(contact.toString()).isEqualTo("Contact[filled]");
        assertThat(style.toString())
                .doesNotContain("Lead with leadership")
                .contains("customInstructions=set");
        assertThat(new Profile(user.userId()).toString()).doesNotContain(user.userId().toString());
    }

    private UUID newUser() {
        return jdbc.queryForObject("INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
    }
}
