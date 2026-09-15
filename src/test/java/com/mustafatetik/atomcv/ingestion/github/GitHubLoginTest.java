package com.mustafatetik.atomcv.ingestion.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The SSRF boundary of the GitHub import (Bolum 42.2, Bolum 31.8).
 *
 * <p>The host is a constant, so the only thing a person decides about the
 * request is the login — and a login goes into a path. Everything here is
 * about the distance between a name and a path segment.
 */
class GitHubLoginTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreTheLocale() {
        Locale.setDefault(original);
    }

    @Test
    void arealLoginIsAccepted() {
        assertThat(GitHubLogin.parse("torvalds")).map(GitHubLogin::value).contains("torvalds");
        assertThat(GitHubLogin.parse("mustafa-tetik"))
                .map(GitHubLogin::value).contains("mustafa-tetik");
        assertThat(GitHubLogin.parse("  spaced  ")).map(GitHubLogin::value).contains("spaced");
    }

    /**
     * <strong>The one that matters.</strong> A value that reaches
     * {@code api.github.com/users/...} carrying a slash or a dot segment is a
     * request to somewhere nobody chose.
     */
    @Test
    void nothingThatCouldLeaveThePathIsAlogin() {
        assertThat(GitHubLogin.parse("../../admin")).isEmpty();
        assertThat(GitHubLogin.parse("torvalds/repos")).isEmpty();
        assertThat(GitHubLogin.parse("..%2fadmin")).isEmpty();
        assertThat(GitHubLogin.parse("a b")).isEmpty();
        assertThat(GitHubLogin.parse("user@host")).isEmpty();
        assertThat(GitHubLogin.parse("")).isEmpty();
        assertThat(GitHubLogin.parse(null)).isEmpty();
    }

    /** GitHub's own grammar: no leading or trailing hyphen, no double hyphen, 39 max. */
    @Test
    void githubsOwnRulesAreTheRules() {
        assertThat(GitHubLogin.parse("-leading")).isEmpty();
        assertThat(GitHubLogin.parse("trailing-")).isEmpty();
        assertThat(GitHubLogin.parse("double--hyphen")).isEmpty();
        assertThat(GitHubLogin.parse("a".repeat(40))).isEmpty();
        assertThat(GitHubLogin.parse("a".repeat(39))).isPresent();
    }

    /** The constructor refuses rather than sanitising: a caller holding one is safe. */
    @Test
    void theconstructorRefusesWhatParseWouldHaveReturnedEmptyFor() {
        assertThatThrownBy(() -> new GitHubLogin("../etc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Absolute rule 7. GitHub logins are case-insensitive, and on a Turkish
     * default locale a bare {@code toLowerCase} writes "ı" for "I" -- so
     * "Ionic" would be looked up as an account nobody has.
     */
    @Test
    void aturkishDefaultLocaleDoesNotChangeTheAccountLookedUp() {
        Locale.setDefault(Locale.forLanguageTag("tr"));

        assertThat(new GitHubLogin("IonicUser").value()).isEqualTo("ionicuser");
    }

    /**
     * What a CV carries is a URL, in whichever of the three shapes the page
     * used.
     */
    @Test
    void theaccountIsReadOutOfWhateverTheCvWrote() {
        assertThat(GitHubLogin.fromProfileUrl("https://github.com/torvalds"))
                .map(GitHubLogin::value).contains("torvalds");
        assertThat(GitHubLogin.fromProfileUrl("https://www.github.com/torvalds/"))
                .map(GitHubLogin::value).contains("torvalds");
        assertThat(GitHubLogin.fromProfileUrl("github.com/torvalds"))
                .map(GitHubLogin::value).contains("torvalds");
        assertThat(GitHubLogin.fromProfileUrl("torvalds"))
                .map(GitHubLogin::value).contains("torvalds");
    }

    /**
     * <strong>A repository URL is not an account.</strong> Somebody whose CV
     * links to one repository -- often somebody else's -- must not have its
     * owner's whole account read as though it were theirs.
     */
    @Test
    void alinkToOneRepositoryIsNotAnaccountToImport() {
        assertThat(GitHubLogin.fromProfileUrl("https://github.com/torvalds/linux"))
                .as("the owner of a repository somebody linked to is not necessarily them")
                .isEmpty();
    }

    @Test
    void nothingAtAllIsNotAnaccount() {
        assertThat(GitHubLogin.fromProfileUrl(null)).isEmpty();
        assertThat(GitHubLogin.fromProfileUrl("   ")).isEmpty();
        assertThat(GitHubLogin.fromProfileUrl("https://gitlab.com/someone")).isEmpty();
    }
}
