package com.mustafatetik.atomcv.identity.challenge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloudflare's siteverify, asked once per sign-in request.
 *
 * <p><strong>{@code remoteip} is deliberately not sent.</strong> Cloudflare
 * accepts it and binds the token to the address, but the address this process
 * believes in depends on {@code server.forward-headers-strategy} being right
 * for the deployment — and a wrong one would turn every sign-in into a
 * refusal that reads as Cloudflare being down. The caller's address is already
 * the key of a layer that runs before this one, which is where it does work
 * that cannot be misconfigured into an outage.
 *
 * <p><strong>A transport failure passes.</strong> Cloudflare being unreachable
 * is not a reason for nobody to be able to sign in, and what the challenge
 * guards is already bounded without it: Bolum 40.5's per-IP and global
 * counters run in front of this call, so the most an outage buys an attacker
 * is the global window. A definite "not successful" is a different thing and
 * is refused — that is Cloudflare answering, not failing to.
 */
class TurnstileChallenge implements Challenge {

    /**
     * Short on purpose. This sits in front of a person waiting on a form, and
     * a challenge service that has stopped answering should not also hold a
     * request thread while it does.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(TurnstileChallenge.class);

    private final TurnstileProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;

    TurnstileChallenge(TurnstileProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                // A verify endpoint answering a redirect is not one we should
                // hand a secret to.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public boolean passed(String token) {
        if (token == null || token.isBlank()) {
            // Not an absence: a client that skipped the widget is the client
            // this exists to stop, and no round trip is needed to know it.
            return false;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.verifyUrl()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "secret=" + encode(properties.secretKey())
                                + "&response=" + encode(token),
                        StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException unreachable) {
            log.warn("Challenge verification did not complete, letting the request "
                    + "through: {}", unreachable.getClass().getSimpleName());
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return true;
        }
        if (response.statusCode() != 200) {
            log.warn("Challenge verification answered {}, letting the request through",
                    response.statusCode());
            return true;
        }
        try {
            JsonNode body = json.readTree(response.body());
            boolean success = body.path("success").asBoolean(false);
            if (!success) {
                // The codes name the token's problem, never the person's.
                log.info("Challenge refused: {}", body.path("error-codes"));
            }
            return success;
        } catch (Exception unreadable) {
            log.warn("Challenge verification answered something unreadable, letting the "
                    + "request through: {}", unreadable.getClass().getSimpleName());
            return true;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
