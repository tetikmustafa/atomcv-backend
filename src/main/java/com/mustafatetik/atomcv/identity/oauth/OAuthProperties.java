package com.mustafatetik.atomcv.identity.oauth;

import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Client credentials per provider, and the address the provider sends the
 * browser back to (Bolum 40.6).
 *
 * <p><strong>A provider with no credentials is absent, not broken.</strong>
 * Bolum 27.3 already established this shape for LLM vendors and it holds here
 * for the same reason: one configuration serves a deployment that has
 * registered one provider and a deployment that has registered both, without
 * either having to list what it is missing.
 *
 * @param redirectBaseUrl the origin the provider redirects to, without a
 *                        trailing slash. It has to be one the frontend serves
 *                        as well: the session cookie is {@code SameSite=Strict}
 *                        and the CSRF token is read from {@code document.cookie},
 *                        so a callback that lands on a different origin than
 *                        the app arrives with neither
 */
@ConfigurationProperties(prefix = "atomcv.oauth")
public record OAuthProperties(
        String redirectBaseUrl,
        String landingPath,
        String errorPath,
        Map<String, Registration> providers) {

    public OAuthProperties {
        redirectBaseUrl = redirectBaseUrl == null || redirectBaseUrl.isBlank()
                ? "http://localhost:3000"
                : redirectBaseUrl.replaceAll("/+$", "");
        landingPath = landingPath == null || landingPath.isBlank()
                ? "/auth/complete"
                : landingPath;
        errorPath = errorPath == null || errorPath.isBlank() ? "/auth/error" : errorPath;
        providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    public Optional<Registration> registrationFor(OAuthProvider provider) {
        return Optional.ofNullable(providers.get(provider.wireValue()))
                // Absolute rule 7: a Turkish locale would not match the key.
                .or(() -> Optional.ofNullable(
                        providers.get(provider.name().toLowerCase(Locale.ROOT))))
                .filter(Registration::isConfigured);
    }

    public boolean isEnabled(OAuthProvider provider) {
        return registrationFor(provider).isPresent();
    }

    /** {@code {redirectBaseUrl}/api/v1/auth/oauth/{provider}/callback}. */
    public String redirectUriFor(OAuthProvider provider) {
        return redirectBaseUrl + "/api/v1/auth/oauth/" + provider.wireValue() + "/callback";
    }

    /**
     * @param authorizationUri where the browser is sent, {@code null} for the
     *                         provider's own. The three endpoint fields exist
     *                         so a test can point an adapter at a local stub;
     *                         no deployment sets them
     * @param tokenUri         where the code is redeemed, {@code null} for the
     *                         provider's own
     * @param apiBaseUrl       where the identity is read, {@code null} for the
     *                         provider's own
     */
    public record Registration(
            String clientId,
            String clientSecret,
            String authorizationUri,
            String tokenUri,
            String apiBaseUrl) {

        /**
         * A static factory and not a second constructor: Spring Boot binds a
         * record through its canonical constructor and refuses to guess when
         * there are two, so the convenience overload left every provider
         * unconfigured and the failure was a sign-in button that quietly did
         * not appear.
         */
        public static Registration of(String clientId, String clientSecret) {
            return new Registration(clientId, clientSecret, null, null, null);
        }

        boolean isConfigured() {
            return clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }

        public String authorizationUriOr(String fallback) {
            return blank(authorizationUri) ? fallback : authorizationUri;
        }

        public String tokenUriOr(String fallback) {
            return blank(tokenUri) ? fallback : tokenUri;
        }

        public String apiBaseUrlOr(String fallback) {
            return blank(apiBaseUrl) ? fallback : apiBaseUrl.replaceAll("/+$", "");
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }
}
