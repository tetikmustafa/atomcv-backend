package com.mustafatetik.atomcv.identity.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A provider with no credentials is absent, not broken (Bolum 27.3's shape). */
class OAuthPropertiesTest {

    private static final OAuthProperties.Registration CONFIGURED =
            OAuthProperties.Registration.of("an-id", "a-secret");

    @Test
    void anUnconfiguredDeploymentOffersNoProvidersAndStillStarts() {
        var properties = new OAuthProperties(null, null, null, null);

        assertThat(properties.isEnabled(OAuthProvider.GOOGLE)).isFalse();
        assertThat(properties.isEnabled(OAuthProvider.GITHUB)).isFalse();
        assertThat(properties.redirectBaseUrl()).isEqualTo("http://localhost:3000");
        assertThat(properties.landingPath()).isEqualTo("/auth/complete");
        assertThat(properties.errorPath()).isEqualTo("/auth/error");
    }

    /**
     * An id with no secret is a half-finished .env, and the failure it would
     * otherwise produce arrives at the provider's consent screen rather than
     * here.
     */
    @Test
    void halfConfiguredIsNotConfigured() {
        var properties = new OAuthProperties(null, null, null,
                Map.of("google", OAuthProperties.Registration.of("an-id", "  ")));

        assertThat(properties.isEnabled(OAuthProvider.GOOGLE)).isFalse();
    }

    @Test
    void oneProviderConfiguredLeavesTheOtherAbsent() {
        var properties = new OAuthProperties(null, null, null, Map.of("github", CONFIGURED));

        assertThat(properties.isEnabled(OAuthProvider.GITHUB)).isTrue();
        assertThat(properties.isEnabled(OAuthProvider.GOOGLE)).isFalse();
    }

    /**
     * The provider matches this string character for character, so a trailing
     * slash on the configured origin is a redirect_uri mismatch and a sign-in
     * that fails only in the deployment that has one.
     */
    @Test
    void theRedirectUriIsBuiltWithoutADoubleSlash() {
        var properties = new OAuthProperties(
                "https://atomcv.example.com/", null, null, Map.of("google", CONFIGURED));

        assertThat(properties.redirectUriFor(OAuthProvider.GOOGLE))
                .isEqualTo("https://atomcv.example.com/api/v1/auth/oauth/google/callback");
    }

    @Test
    void anEndpointOverrideIsUsedOnlyWhenGiven() {
        var registration = new OAuthProperties.Registration(
                "an-id", "a-secret", "http://stub/authorize", null, "http://stub/api/");

        assertThat(registration.authorizationUriOr("https://real")).isEqualTo("http://stub/authorize");
        assertThat(registration.tokenUriOr("https://real/token")).isEqualTo("https://real/token");
        assertThat(registration.apiBaseUrlOr("https://real")).isEqualTo("http://stub/api");
    }
}
