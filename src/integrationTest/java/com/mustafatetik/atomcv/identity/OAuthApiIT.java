package com.mustafatetik.atomcv.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The whole round trip, against a provider that answers on a real socket
 * (Bolum 40.6).
 *
 * <p>The endpoint overrides on {@code OAuthProperties.Registration} exist for
 * this: with them the adapters talk to a stub, so the flow the user actually
 * walks — start, consent, callback, cookie, account — is exercised end to end
 * rather than asserted one class at a time.
 */
@AutoConfigureMockMvc
class OAuthApiIT extends AbstractIntegrationTest {

    private static final HttpServer PROVIDER;

    private static final Map<String, String> RESPONSES = new LinkedHashMap<>();

    static {
        try {
            PROVIDER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException cannotBind) {
            throw new IllegalStateException(cannotBind);
        }
        PROVIDER.createContext("/", OAuthApiIT::answer);
        PROVIDER.start();
    }

    private static String base() {
        return "http://127.0.0.1:" + PROVIDER.getAddress().getPort();
    }

    private static void answer(HttpExchange exchange) throws IOException {
        String body = RESPONSES.get(exchange.getRequestURI().getPath());
        byte[] bytes = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(body == null ? 500 : 200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @DynamicPropertySource
    static void pointTheAdaptersAtTheStub(DynamicPropertyRegistry registry) {
        registry.add("atomcv.oauth.redirect-base-url", () -> "http://app.test");
        registry.add("atomcv.oauth.providers.google.client-id", () -> "an-id");
        registry.add("atomcv.oauth.providers.google.client-secret", () -> "a-secret");
        registry.add("atomcv.oauth.providers.google.authorization-uri", () -> base() + "/authorize");
        registry.add("atomcv.oauth.providers.google.token-uri", () -> base() + "/token");
        registry.add("atomcv.oauth.providers.google.api-base-url", () -> base() + "/userinfo");
        // GitHub stays unconfigured on purpose: /providers has to say so.
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearProviderAndAccounts() {
        RESPONSES.clear();
        jdbc.update("DELETE FROM oauth_identities");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@oauth.test'");
    }

    @AfterEach
    void clearAgain() {
        RESPONSES.clear();
    }

    @Test
    void onlyTheConfiguredProviderIsOffered() throws Exception {
        mvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.contains("google")));
    }

    @Test
    void startingSendsTheBrowserToTheProviderWithAState() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/oauth/google/start"))
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).startsWith(base() + "/authorize?");
        assertThat(location).contains("state=");
    }

    /** Unknown and unconfigured are the same answer: there is no such button. */
    @Test
    void startingAtAProviderThisDeploymentHasNoCredentialsForIsAnErrorPage() throws Exception {
        mvc.perform(get("/api/v1/auth/oauth/github/start"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://app.test/auth/error?code=OAUTH_FAILED&reason=provider_disabled"));

        mvc.perform(get("/api/v1/auth/oauth/linkedin/start"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://app.test/auth/error?code=OAUTH_FAILED&reason=provider_disabled"));
    }

    /**
     * The guard, made to fail. A callback nobody started must not reach the
     * provider with a code, and must not set a cookie.
     */
    @Test
    void aCallbackWithAForgedStateIsRefusedBeforeAnythingElseHappens() throws Exception {
        mvc.perform(get("/api/v1/auth/oauth/google/callback")
                        .param("code", "a-code")
                        .param("state", "not-a-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://app.test/auth/error?code=OAUTH_FAILED&reason=state_invalid"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void cancellingAtTheProviderIsReportedAsCancelledAndNotAsAFault() throws Exception {
        String state = beginAndTakeState();

        mvc.perform(get("/api/v1/auth/oauth/google/callback")
                        .param("state", state)
                        .param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://app.test/auth/error?code=OAUTH_FAILED&reason=declined"));
    }

    @Test
    void anUnverifiedAddressNeverBecomesAnAccount() throws Exception {
        RESPONSES.put("/token", "{\"access_token\": \"a-token\"}");
        RESPONSES.put("/userinfo", """
                {"sub": "sub-9", "email": "unverified@oauth.test", "email_verified": false}""");
        String state = beginAndTakeState();

        mvc.perform(get("/api/v1/auth/oauth/google/callback")
                        .param("code", "a-code").param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://app.test/auth/error?code=OAUTH_FAILED&reason=email_unverified"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        assertThat(countUsers("unverified@oauth.test")).isZero();
    }

    /**
     * The whole point of the slice: a person who has never been here before
     * comes back from the provider with an account, a session and a cookie
     * that works on the next request.
     */
    @Test
    void aFirstSignInCreatesTheAccountAndTheSessionCookieWorks() throws Exception {
        RESPONSES.put("/token", "{\"access_token\": \"a-token\"}");
        RESPONSES.put("/userinfo", """
                {"sub": "sub-1", "email": "ada@oauth.test",
                 "email_verified": true, "name": "Ada Lovelace"}""");
        String state = beginAndTakeState("/profile");

        MvcResult callback = mvc.perform(get("/api/v1/auth/oauth/google/callback")
                        .param("code", "a-code").param("state", state))
                .andExpect(status().isFound())
                .andReturn();

        // Not the destination: a Strict cookie is withheld from a redirect
        // chain that began at the provider, so the client lands here and asks
        // /auth/session with a same-origin fetch before routing on.
        assertThat(callback.getResponse().getHeader("Location"))
                .isEqualTo("http://app.test/auth/complete?next=%2Fprofile&profile=none");

        String sid = sessionCookieOf(callback);
        assertThat(sid).isNotBlank();

        mvc.perform(get("/api/v1/auth/session").cookie(new Cookie("sid", sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));

        assertThat(countUsers("ada@oauth.test")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM oauth_identities WHERE provider = 'google'", Integer.class))
                .isEqualTo(1);
    }

    /**
     * The second visit is the same account, not a second one — and the
     * database proves it, because the unique constraint would have refused a
     * duplicate binding.
     */
    @Test
    void comingBackASecondTimeReusesTheAccount() throws Exception {
        RESPONSES.put("/token", "{\"access_token\": \"a-token\"}");
        RESPONSES.put("/userinfo", """
                {"sub": "sub-2", "email": "returning@oauth.test",
                 "email_verified": true, "name": "Ada"}""");

        signInOnce();
        signInOnce();

        assertThat(countUsers("returning@oauth.test")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM oauth_identities", Integer.class)).isEqualTo(1);
    }

    /**
     * {@code users.email} is {@code CITEXT}, so an address that differs only
     * in case is the same account. Without it a provider changing how it
     * cases an address would silently fork the person's data in two.
     */
    @Test
    void anAddressDifferingOnlyInCaseIsTheSameAccount() throws Exception {
        RESPONSES.put("/token", "{\"access_token\": \"a-token\"}");
        RESPONSES.put("/userinfo", """
                {"sub": "sub-3", "email": "Mixed.Case@oauth.test", "email_verified": true}""");
        signInOnce();

        // A different subject, so the lookup falls through to the address.
        RESPONSES.put("/userinfo", """
                {"sub": "sub-4", "email": "mixed.case@oauth.test", "email_verified": true}""");
        signInOnce();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE email = 'MIXED.CASE@oauth.test'", Integer.class))
                .isEqualTo(1);
        // One account, two providers' worth of identities bound to it.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM oauth_identities", Integer.class)).isEqualTo(2);
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private void signInOnce() throws Exception {
        String state = beginAndTakeState();
        mvc.perform(get("/api/v1/auth/oauth/google/callback")
                        .param("code", "a-code").param("state", state))
                .andExpect(status().isFound());
    }

    private String beginAndTakeState() throws Exception {
        return beginAndTakeState("/");
    }

    private String beginAndTakeState(String next) throws Exception {
        MvcResult started = mvc.perform(get("/api/v1/auth/oauth/google/start").param("next", next))
                .andExpect(status().isFound())
                .andReturn();
        String query = URI.create(started.getResponse().getHeader("Location")).getQuery();
        for (String pair : query.split("&")) {
            if (pair.startsWith("state=")) {
                return pair.substring("state=".length());
            }
        }
        throw new IllegalStateException("The authorization URL carried no state");
    }

    private static String sessionCookieOf(MvcResult result) {
        jakarta.servlet.http.Cookie cookie = result.getResponse().getCookie("sid");
        return cookie == null ? null : cookie.getValue();
    }

    private int countUsers(String email) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
    }
}
