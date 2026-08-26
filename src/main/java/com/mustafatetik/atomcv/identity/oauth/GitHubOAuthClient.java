package com.mustafatetik.atomcv.identity.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.identity.domain.OAuthAccount;
import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * GitHub, which needs two profile calls rather than one (Bolum 40.6).
 *
 * <p><strong>{@code /user} is not enough.</strong> Its {@code email} field is
 * whatever the person set as publicly visible, and it is {@code null} for
 * anyone who keeps it private — a large share of developers, which is most of
 * this product's users. Worse, it carries no verification flag at all, so
 * trusting it would mean linking accounts on an address GitHub does not vouch
 * for. {@code /user/emails} carries {@code primary} and {@code verified}, and
 * is the only source here that can answer the question the linking rule asks.
 */
@Component
public class GitHubOAuthClient implements OAuthClient {

    private static final String AUTHORIZATION = "https://github.com/login/oauth/authorize";

    private static final String TOKEN = "https://github.com/login/oauth/access_token";

    private static final String API = "https://api.github.com";

    private final OAuthProperties properties;
    private final OAuthHttp http;

    GitHubOAuthClient(OAuthProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.http = new OAuthHttp(json);
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GITHUB;
    }

    @Override
    public URI authorizationUri(String state) {
        var registration = registration();
        var params = new LinkedHashMap<String, String>();
        params.put("client_id", registration.clientId());
        // GitHub accepts a single callback URL per OAuth App, so development
        // and production are two applications. Sending it explicitly still
        // matters: GitHub matches it against the registered one.
        params.put("redirect_uri", properties.redirectUriFor(provider()));
        // read:user for the account, user:email for the verified address.
        // Not `repo`, not `read:org` — the ingestion module will ask for what
        // it needs when it exists, and a scope granted early is a scope the
        // person cannot see a reason for.
        params.put("scope", "read:user user:email");
        params.put("state", state);
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

        Optional<String> accessToken = http.postForm(registration.tokenUriOr(TOKEN), form)
                .map(body -> body.path("access_token").asText(null))
                .filter(token -> token != null && !token.isBlank());
        if (accessToken.isEmpty()) {
            // GitHub answers 200 with {"error": "bad_verification_code"} for a
            // reused code, so an absent token is the signal, not the status.
            return OAuthExchange.failed(OAuthFailure.PROVIDER_UNAVAILABLE);
        }

        String api = registration.apiBaseUrlOr(API);
        Optional<JsonNode> user = http.getWithBearer(api + "/user", accessToken.get());
        if (user.isEmpty()) {
            return OAuthExchange.failed(OAuthFailure.PROVIDER_UNAVAILABLE);
        }
        String subject = user.get().path("id").asText(null);
        if (subject == null || subject.isBlank() || "null".equals(subject)) {
            return OAuthExchange.failed(OAuthFailure.PROVIDER_UNAVAILABLE);
        }

        Optional<JsonNode> emails = http.getWithBearer(api + "/user/emails", accessToken.get());
        if (emails.isEmpty()) {
            return OAuthExchange.failed(OAuthFailure.PROVIDER_UNAVAILABLE);
        }
        return accountFrom(subject, displayNameOf(user.get()), emails.get());
    }

    /**
     * The primary address, and only if GitHub has verified it. A verified
     * non-primary address is not used: the person chose which address
     * represents them, and picking a different one would attach the account to
     * an address they do not expect.
     */
    private OAuthExchange accountFrom(String subject, String displayName, JsonNode emails) {
        if (!emails.isArray()) {
            return OAuthExchange.failed(OAuthFailure.PROVIDER_UNAVAILABLE);
        }
        for (JsonNode entry : emails) {
            if (!entry.path("primary").asBoolean(false)) {
                continue;
            }
            String address = entry.path("email").asText(null);
            if (address == null || address.isBlank()) {
                return OAuthExchange.failed(OAuthFailure.EMAIL_MISSING);
            }
            return entry.path("verified").asBoolean(false)
                    ? OAuthExchange.of(new OAuthAccount(
                            provider(), subject, address, true, displayName))
                    : OAuthExchange.failed(OAuthFailure.EMAIL_UNVERIFIED);
        }
        return OAuthExchange.failed(OAuthFailure.EMAIL_MISSING);
    }

    /** {@code name} is optional on GitHub; the login handle always exists. */
    private static String displayNameOf(JsonNode user) {
        String name = user.path("name").asText(null);
        return name == null || name.isBlank() ? user.path("login").asText(null) : name;
    }

    private OAuthProperties.Registration registration() {
        return properties.registrationFor(provider()).orElseThrow(
                () -> new IllegalStateException("GitHub is not configured"));
    }
}
