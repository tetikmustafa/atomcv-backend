package com.mustafatetik.atomcv.identity.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Bolum 44.4 with the challenge actually switched on.
 *
 * <p>The rest of the suite runs without a secret, which is the local branch and
 * the right one there — but it means nothing else in this repository ever
 * watches the endpoint <em>refuse</em>. A guard that has never failed is not
 * known to work, so this class pays for its own application context to hold
 * one that is configured, pointed at a stub that answers the way Cloudflare
 * does.
 *
 * <p>What it asserts is the part the unit test cannot see: that a refusal
 * stops the request before anything is created or sent.
 */
@AutoConfigureMockMvc
class ChallengeApiIT extends AbstractIntegrationTest {

    private static final HttpServer VERIFIER = stub();

    /** Flipped per test; the stub answers whatever it says at the time. */
    private static volatile boolean accepts = true;

    @DynamicPropertySource
    static void pointTheChallengeAtTheStub(DynamicPropertyRegistry registry) {
        registry.add("atomcv.turnstile.secret-key", () -> "a-secret");
        registry.add("atomcv.turnstile.verify-url",
                () -> "http://127.0.0.1:" + VERIFIER.getAddress().getPort() + "/siteverify");
    }

    private static HttpServer stub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/siteverify", ChallengeApiIT::answer);
            server.start();
            return server;
        } catch (IOException impossible) {
            throw new IllegalStateException("could not open a stub verifier", impossible);
        }
    }

    private static void answer(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = ("{\"success\": " + accepts + "}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clear() {
        accepts = true;
        jdbc.update("DELETE FROM users WHERE email LIKE '%@challenge.test'");
        Set<String> counters = redis.keys("ratelimit:*");
        if (counters != null && !counters.isEmpty()) {
            redis.delete(counters);
        }
    }

    @Test
    void aTokenTheVerifierAcceptsGetsThroughToTheLink() throws Exception {
        askForALink("ada@challenge.test", "\"a-token\"")
                .andExpect(status().isAccepted());

        assertThat(accountsFor("ada@challenge.test")).isOne();
    }

    /**
     * The refusal, and what it must leave behind: nothing.
     *
     * <p>Bolum 40.4.1's whole worry is that this endpoint creates a user row
     * for any address anyone types. A challenge that answered 403 after the
     * row was written would be a check on the response and not on the effect.
     */
    @Test
    void aTokenTheVerifierRejectsCreatesNoAccount() throws Exception {
        accepts = false;

        askForALink("stranger@challenge.test", "\"a-spent-token\"")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHALLENGE_FAILED"))
                .andExpect(jsonPath("$.params").doesNotExist());

        assertThat(accountsFor("stranger@challenge.test")).isZero();
    }

    /** A client that skipped the widget is the client this exists to stop. */
    @Test
    void aRequestWithNoTokenAtAllIsRefusedTheSameWay() throws Exception {
        mvc.perform(post("/api/v1/auth/magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"stranger@challenge.test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHALLENGE_FAILED"));

        assertThat(accountsFor("stranger@challenge.test")).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions askForALink(
            String email, String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/magic-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"challengeToken\": " + token + "}"));
    }

    private int accountsFor(String email) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE email = CAST(? AS citext)",
                Integer.class, email);
        return count == null ? 0 : count;
    }
}
