package com.mustafatetik.atomcv.identity.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.identity.domain.AuthMethod;
import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The vocabulary the database column and the authorization URLs both spell out. */
class OAuthProviderTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    void theWireValuesAreTheOnesTheColumnAllows() {
        assertThat(OAuthProvider.GOOGLE.wireValue()).isEqualTo("google");
        assertThat(OAuthProvider.GITHUB.wireValue()).isEqualTo("github");
    }

    /**
     * Absolute rule 7, watched failing. In the Turkish locale lowercasing
     * {@code GITHUB} yields a dotless i, so a lookup written without
     * {@link Locale#ROOT} misses on the developer's own machine and nowhere
     * else — the CI runner is UTF-8 and English, which is exactly how a bug
     * like this ships.
     */
    @Test
    void aTurkishDefaultLocaleDoesNotBreakTheLookup() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        assertThat(OAuthProvider.fromWire("GITHUB")).contains(OAuthProvider.GITHUB);
        assertThat(OAuthProvider.fromWire("GitHub")).contains(OAuthProvider.GITHUB);
        assertThat(OAuthProvider.fromWire("google")).contains(OAuthProvider.GOOGLE);
    }

    @Test
    void anythingOutsideTheVocabularyIsEmptyRatherThanAnException() {
        assertThat(OAuthProvider.fromWire("linkedin")).isEmpty();
        assertThat(OAuthProvider.fromWire("")).isEmpty();
        assertThat(OAuthProvider.fromWire(null)).isEmpty();
        assertThat(OAuthProvider.fromWire("../../etc/passwd")).isEmpty();
    }

    /** A provider added without an auth method would not compile; this says so out loud. */
    @Test
    void everyProviderRecordsHowTheSessionWasMade() {
        assertThat(Arrays.stream(OAuthProvider.values()).map(OAuthProvider::authMethod))
                .containsExactly(AuthMethod.OAUTH_GOOGLE, AuthMethod.OAUTH_GITHUB);
    }
}
