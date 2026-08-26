package com.mustafatetik.atomcv.identity.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.identity.domain.OAuthAccount;
import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Google, through OpenID Connect's userinfo endpoint (Bolum 40.6).
 *
 * <p><strong>The {@code id_token} is deliberately ignored.</strong> Its claims
 * carry everything needed, but reading them means either verifying an RS256
 * signature against a rotating JWKS or trusting an unverified JWT — the first
 * is a dependency and a key-rotation bug waiting to happen, the second is the
 * classic OAuth mistake. One extra call to userinfo over TLS, with the access
 * token we just received from the token endpoint, answers the same question
 * with nothing to get wrong.
 */
@Component
public class GoogleOAuthClient implements OAuthClient {

    private static final String AUTHORIZATION = "https://accounts.google.com/o/oauth2/v2/auth";

    private static final String TOKEN = "https://oauth2.googleapis.com/token";

    private static final String USERINFO = "https://openidconnect.googleapis.com/v1/userinfo";

    private final OAuthProperties properties;
    private final OAuthHttp http;

    GoogleOAuthClient(OAuthProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.http = new OAuthHttp(json);
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    public URI authorizationUri(String state) {
        var registration = registration();
        var params = new LinkedHashMap<String, String>();
        params.put("client_id", registration.clientId());
        params.put("redirect_uri", properties.redirectUriFor(provider()));
        params.put("response_type", "code");
        // The three OIDC scopes and nothing else. Anything beyond them puts
        // the app into Google's verification queue, which takes weeks and buys
        // data we would then be holding without needing.
        params.put("scope", "openid email profile");
        params.put("state", state);
        // Online: no refresh token is issued, because nothing here calls
        // Google again once the person is signed in.
        params.put("access_type", "online");
        // Otherwise a browser with one Google session signs in silently, and
        // "sign in as someone else" becomes impossible without signing out of
        // Google itself.
        params.put("prompt", "select_account");
        return URI.create(registration.authorizationUriOr(AUTHORIZATION)
                + "?" + OAuthHttp.queryString(params));
    }

    @Override
    public OAuthExchange exchange(String code) {
        var registration = registration();
        var form = new LinkedHashMap<String, String>();
        form.put("code", code);
        form.put("client_id", registration.clientId());
        form.put("client_secret", registration.clientSecret());
        form.put("redirect_uri", properties.redirectUriFor(provider()));
        form.put("grant_type", "authorization_code");

        Optional<String> accessToken = http.postForm(registration.tokenUriOr(TOKEN), form)
                .map(body -> body.path("access_token").asText(null))
                .filter(token -> token != null && !token.isBlank());
        if (accessToken.isEmpty()) {
            return OAuthExchange.failed(OAuthFailure.PROVIDER_UNAVAILABLE);
        }

        Optional<JsonNode> profile = http.getWithBearer(
                registration.apiBaseUrlOr(USERINFO), accessToken.get());
        if (profile.isEmpty()) {
            return OAuthExchange.failed(OAuthFailure.PROVIDER_UNAVAILABLE);
        }
        return accountFrom(profile.get());
    }

    private OAuthExchange accountFrom(JsonNode profile) {
        String subject = profile.path("sub").asText(null);
        String email = profile.path("email").asText(null);
        if (subject == null || subject.isBlank()) {
            return OAuthExchange.failed(OAuthFailure.PROVIDER_UNAVAILABLE);
        }
        if (email == null || email.isBlank()) {
            return OAuthExchange.failed(OAuthFailure.EMAIL_MISSING);
        }
        // Absent is not true. A userinfo response without the claim is one we
        // cannot vouch for, and defaulting it to verified is the whole attack.
        if (!profile.path("email_verified").asBoolean(false)) {
            return OAuthExchange.failed(OAuthFailure.EMAIL_UNVERIFIED);
        }
        return OAuthExchange.of(new OAuthAccount(
                provider(), subject, email, true, profile.path("name").asText(null)));
    }

    private OAuthProperties.Registration registration() {
        return properties.registrationFor(provider()).orElseThrow(
                () -> new IllegalStateException("Google is not configured"));
    }
}
