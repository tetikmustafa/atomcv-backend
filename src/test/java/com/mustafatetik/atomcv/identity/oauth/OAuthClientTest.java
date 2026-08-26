package com.mustafatetik.atomcv.identity.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Both adapters against a real socket.
 *
 * <p>A stub server rather than a mocked {@code HttpClient}: what these classes
 * actually do is read a provider's JSON, and the mistakes worth catching —
 * a missing claim read as {@code true}, a private GitHub address, a token
 * endpoint that answers 200 with an error body — all live in that reading.
 * Mocking the client would assert the code against my idea of the provider
 * instead of against a response.
 */
class OAuthClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private String base;
    private final Map<String, String> responses = new LinkedHashMap<>();

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        String body = responses.get(exchange.getRequestURI().getPath());
        byte[] bytes = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(body == null ? 500 : 200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    // ── Google ────────────────────────────────────────────────────────────

    @Test
    void googleReturnsTheSubjectAndTheVerifiedAddress() {
        responses.put("/token", "{\"access_token\": \"a-token\"}");
        responses.put("/userinfo", """
                {"sub": "108", "email": "ada@example.com",
                 "email_verified": true, "name": "Ada Lovelace"}""");

        var exchange = google().exchange("a-code");

        assertThat(exchange).isInstanceOf(OAuthExchange.Account.class);
        var account = ((OAuthExchange.Account) exchange).value();
        assertThat(account.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(account.providerUid()).isEqualTo("108");
        assertThat(account.email()).isEqualTo("ada@example.com");
        assertThat(account.emailVerified()).isTrue();
        assertThat(account.displayName()).isEqualTo("Ada Lovelace");
    }

    /**
     * Absent is not true. A userinfo response without the claim is one we
     * cannot vouch for, and defaulting it to verified is the whole attack.
     */
    @Test
    void googleWithoutTheVerifiedClaimIsRefused() {
        responses.put("/token", "{\"access_token\": \"a-token\"}");
        responses.put("/userinfo", "{\"sub\": \"108\", \"email\": \"ada@example.com\"}");

        assertThat(google().exchange("a-code"))
                .isEqualTo(OAuthExchange.failed(OAuthFailure.EMAIL_UNVERIFIED));
    }

    @Test
    void googleSayingTheAddressIsUnverifiedIsRefused() {
        responses.put("/token", "{\"access_token\": \"a-token\"}");
        responses.put("/userinfo", """
                {"sub": "108", "email": "ada@example.com", "email_verified": false}""");

        assertThat(google().exchange("a-code"))
                .isEqualTo(OAuthExchange.failed(OAuthFailure.EMAIL_UNVERIFIED));
    }

    @Test
    void aTokenEndpointThatAnswersWithoutATokenIsAnUnavailableProvider() {
        responses.put("/token", "{\"error\": \"invalid_grant\"}");

        assertThat(google().exchange("a-reused-code"))
                .isEqualTo(OAuthExchange.failed(OAuthFailure.PROVIDER_UNAVAILABLE));
    }

    @Test
    void aProviderThatIsDownIsNotAnAccount() {
        // No stubbed responses at all: the server answers 500.
        assertThat(google().exchange("a-code"))
                .isEqualTo(OAuthExchange.failed(OAuthFailure.PROVIDER_UNAVAILABLE));
    }

    @Test
    void googleAsksForTheThreeScopesAndNothingMore() {
        var uri = google().authorizationUri("a-state").toString();

        assertThat(uri).startsWith(base + "/authorize?");
        assertThat(uri).contains("scope=openid+email+profile");
        assertThat(uri).contains("state=a-state");
        assertThat(uri).contains("response_type=code");
        // Without it, a browser with one Google session signs in silently and
        // "use another account" becomes impossible.
        assertThat(uri).contains("prompt=select_account");
        assertThat(uri).contains(
                "redirect_uri=http%3A%2F%2Fapp.test%2Fapi%2Fv1%2Fauth%2Foauth%2Fgoogle%2Fcallback");
    }

    // ── GitHub ────────────────────────────────────────────────────────────

    /**
     * The reason GitHub needs two calls: {@code /user} carries whatever the
     * person made publicly visible, which for most developers is
     * {@code null} — and it carries no verification flag at all.
     */
    @Test
    void githubReadsThePrimaryVerifiedAddressRatherThanThePublicOne() {
        responses.put("/token", "{\"access_token\": \"a-token\"}");
        responses.put("/user", "{\"id\": 4711, \"login\": \"ada\", \"name\": null, \"email\": null}");
        responses.put("/user/emails", """
                [{"email": "public@example.com", "primary": false, "verified": true},
                 {"email": "ada@example.com", "primary": true, "verified": true}]""");

        var exchange = github().exchange("a-code");

        var account = ((OAuthExchange.Account) exchange).value();
        assertThat(account.providerUid()).isEqualTo("4711");
        assertThat(account.email()).isEqualTo("ada@example.com");
        // name is null on GitHub far more often than not; the handle stands in.
        assertThat(account.displayName()).isEqualTo("ada");
    }

    @Test
    void githubWithAnUnverifiedPrimaryAddressIsRefused() {
        responses.put("/token", "{\"access_token\": \"a-token\"}");
        responses.put("/user", "{\"id\": 4711, \"login\": \"ada\"}");
        responses.put("/user/emails", """
                [{"email": "ada@example.com", "primary": true, "verified": false}]""");

        assertThat(github().exchange("a-code"))
                .isEqualTo(OAuthExchange.failed(OAuthFailure.EMAIL_UNVERIFIED));
    }

    /**
     * A verified non-primary address is not a substitute. The person chose
     * which address represents them, and attaching the account to another one
     * would surprise them later.
     */
    @Test
    void githubWithNoPrimaryAddressAtAllIsRefused() {
        responses.put("/token", "{\"access_token\": \"a-token\"}");
        responses.put("/user", "{\"id\": 4711, \"login\": \"ada\"}");
        responses.put("/user/emails", """
                [{"email": "other@example.com", "primary": false, "verified": true}]""");

        assertThat(github().exchange("a-code"))
                .isEqualTo(OAuthExchange.failed(OAuthFailure.EMAIL_MISSING));
    }

    @Test
    void githubAsksOnlyForTheScopesSignInNeeds() {
        var uri = github().authorizationUri("a-state").toString();

        assertThat(uri).contains("scope=read%3Auser+user%3Aemail");
        assertThat(uri).doesNotContain("repo");
        assertThat(uri).contains("state=a-state");
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private GoogleOAuthClient google() {
        return new GoogleOAuthClient(propertiesWith("google",
                base + "/authorize", base + "/token", base + "/userinfo"), JSON);
    }

    private GitHubOAuthClient github() {
        return new GitHubOAuthClient(propertiesWith("github",
                base + "/authorize", base + "/token", base), JSON);
    }

    private static OAuthProperties propertiesWith(
            String provider, String authorize, String token, String api) {
        return new OAuthProperties("http://app.test", null, null,
                Map.of(provider, new OAuthProperties.Registration(
                        "an-id", "a-secret", authorize, token, api)));
    }
}
