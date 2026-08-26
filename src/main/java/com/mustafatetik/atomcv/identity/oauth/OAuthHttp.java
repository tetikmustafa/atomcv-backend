package com.mustafatetik.atomcv.identity.oauth;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The two HTTP shapes every OAuth provider needs, in one place: a form POST
 * that redeems a code, and a bearer GET that reads an identity.
 *
 * <p><strong>Nothing here logs a body.</strong> A token response contains a
 * credential and a profile response contains an email; the status and the
 * shape of the failure are enough to diagnose either, and neither ever needs
 * to reach a log line (absolute rule 4 and its spirit).
 */
final class OAuthHttp {

    /**
     * A consent screen can be slow; a token endpoint answering it should not
     * be. Long enough to absorb a slow hop, short enough that a hung provider
     * does not hold a request thread through the user's whole coffee break.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final Logger log = LoggerFactory.getLogger(OAuthHttp.class);

    private final HttpClient http;
    private final ObjectMapper json;

    OAuthHttp(ObjectMapper json) {
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // Never follow a redirect: a token endpoint that answers 302
                // is not one we should hand a client secret to.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    Optional<JsonNode> postForm(String uri, Map<String, String> form) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                // GitHub answers form-encoded unless asked otherwise.
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form), StandardCharsets.UTF_8))
                .build();
        return send(request, "token");
    }

    Optional<JsonNode> getWithBearer(String uri, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(request, "profile");
    }

    private Optional<JsonNode> send(HttpRequest request, String what) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException unreachable) {
            log.warn("OAuth {} call failed: {}", what, unreachable.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("OAuth {} call interrupted", what);
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            // The status, never the body: an OAuth error body echoes back the
            // request, and the request carries the client secret.
            log.warn("OAuth {} call answered http {}", what, response.statusCode());
            return Optional.empty();
        }
        try {
            return Optional.of(json.readTree(response.body()));
        } catch (Exception unreadable) {
            log.warn("OAuth {} response was not readable JSON", what);
            return Optional.empty();
        }
    }

    private static String encode(Map<String, String> form) {
        var ordered = new LinkedHashMap<>(form);
        var out = new StringBuilder();
        ordered.forEach((key, value) -> {
            if (!out.isEmpty()) {
                out.append('&');
            }
            out.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return out.toString();
    }

    /** Shared by the adapters for building an authorization URL. */
    static String queryString(Map<String, String> params) {
        return encode(params);
    }
}
