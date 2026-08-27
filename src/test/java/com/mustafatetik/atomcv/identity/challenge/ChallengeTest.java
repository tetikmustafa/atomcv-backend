package com.mustafatetik.atomcv.identity.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * The challenge against a real socket, the way {@code OAuthClientTest} does
 * its providers.
 *
 * <p>What is worth catching here lives in the reading of a response, not in a
 * mocked client: a body with no {@code success} field read as {@code true}, a
 * 500 turned into a refusal, a blank token that costs a round trip. A mock
 * would assert this code against my idea of Cloudflare.
 */
class ChallengeTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private String url;
    private String body = "{\"success\": true}";
    private int status = 200;
    private final List<String> received = new ArrayList<>();

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/siteverify", this::answer);
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify";
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    // -- verifying ---------------------------------------------------------

    @Test
    void aTokenCloudflareAcceptsPasses() {
        assertThat(challenge().passed("a-token")).isTrue();
        assertThat(received).singleElement().asString()
                .contains("response=a-token")
                .contains("secret=a-secret");
    }

    /**
     * And the address is not sent with it. Cloudflare would bind the token to
     * it, but the address this process believes in depends on
     * {@code forward-headers-strategy}, and a wrong one would turn every
     * sign-in into a refusal that reads as Cloudflare being down.
     */
    @Test
    void theCallersAddressIsNotSentWithIt() {
        challenge().passed("a-token");

        assertThat(received).singleElement().asString().doesNotContain("remoteip");
    }

    @Test
    void aTokenCloudflareRejectsDoesNotPass() {
        body = "{\"success\": false, \"error-codes\": [\"timeout-or-duplicate\"]}";

        assertThat(challenge().passed("a-spent-token")).isFalse();
    }

    /**
     * A body with no {@code success} field has to read as a refusal. It is the
     * shape a proxy or an error page produces, and defaulting it the other way
     * would let anything that is not Cloudflare answer for Cloudflare.
     */
    @Test
    void aBodyThatNeverSaysSuccessDoesNotPass() {
        body = "{\"error-codes\": []}";

        assertThat(challenge().passed("a-token")).isFalse();
    }

    @Test
    void aMissingTokenIsRefusedWithoutAskingAnybody() {
        assertThat(challenge().passed(null)).isFalse();
        assertThat(challenge().passed("  ")).isFalse();

        // The point of the case: a client that skipped the widget must not
        // cost a round trip to Cloudflare on every attempt.
        assertThat(received).isEmpty();
    }

    // -- when Cloudflare is the one having a bad day -----------------------

    /**
     * A transport failure passes, and that is the deliberate half.
     *
     * <p>Cloudflare being unreachable is not a reason for nobody to be able to
     * sign in, and Bolum 40.5's per-IP and global counters run in front of
     * this call — so the most an outage buys is the global window. A refusal
     * here would turn their outage into ours.
     */
    @Test
    void anUnreachableVerifierLetsTheRequestThrough() {
        Challenge unreachable = new TurnstileChallenge(
                new TurnstileProperties("a-secret", "http://127.0.0.1:1/siteverify"), JSON);

        assertThat(unreachable.passed("a-token")).isTrue();
    }

    @Test
    void aVerifierAnsweringAnErrorStatusLetsTheRequestThrough() {
        status = 500;
        body = "{}";

        assertThat(challenge().passed("a-token")).isTrue();
    }

    @Test
    void aVerifierAnsweringSomethingUnreadableLetsTheRequestThrough() {
        body = "<html>we are down</html>";

        assertThat(challenge().passed("a-token")).isTrue();
    }

    // -- which challenge a deployment gets (ChallengeConfig) ---------------

    @Test
    void aSecretBuildsTheRealChallenge() {
        Challenge built = new ChallengeConfig().challenge(
                new TurnstileProperties("a-secret", url), JSON, new MockEnvironment());

        assertThat(built).isInstanceOf(TurnstileChallenge.class);
    }

    @Test
    void noSecretOutsideProductionWavesRequestsThroughRatherThanFailing() {
        Challenge built = new ChallengeConfig().challenge(
                new TurnstileProperties("", url), JSON, new MockEnvironment());

        assertThat(built).isNotInstanceOf(TurnstileChallenge.class);
        assertThat(built.passed(null)).isTrue();
    }

    /**
     * The door {@code EmailSenderConfig} does not need. A deployment with no
     * sender is discovered by the first person who never got their link; a
     * deployment with no challenge works perfectly and is open.
     */
    @Test
    void noSecretInProductionRefusesToStart() {
        var production = new MockEnvironment();
        production.setActiveProfiles("prod");

        assertThatThrownBy(() -> new ChallengeConfig().challenge(
                new TurnstileProperties("", url), JSON, production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TURNSTILE_SECRET_KEY");
    }

    private Challenge challenge() {
        return new TurnstileChallenge(new TurnstileProperties("a-secret", url), JSON);
    }
}
