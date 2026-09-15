package com.mustafatetik.atomcv.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The deployed Content-Security-Policy against what the product loads.
 *
 * <p><strong>Written because it was wrong, and would have stayed wrong until
 * launch.</strong> The policy is {@code default-src 'self'} with no host
 * named, and Turnstile is a script this origin loads from {@code
 * challenges.cloudflare.com} and an iframe this origin embeds from the same
 * host. CSP blocks both without an error anyone sees: the widget never
 * renders, no token is ever produced, and sign-in, anonymous import and
 * anonymous generation all answer {@code CHALLENGE_FAILED}. Nothing in either
 * repository's test suite touches nginx, and the local profile hands every
 * token a {@code true}, so the first run of the guard would have been the
 * first user's.
 *
 * <p><strong>Why a test over a config file.</strong> This one cannot fail in
 * CI the way a broken query fails — nginx is not started here. What it can do
 * is fail when somebody tightens the policy back to the specification's
 * literal text, which is exactly how the hole was dug: the original snippet
 * predates Turnstile and still reads as the finished answer.
 */
class ContentSecurityPolicyTest {

    /** From the repository root, which is where Gradle runs a test from. */
    private static final Path NGINX = Path.of("docker/nginx/nginx.conf");

    private static final String TURNSTILE = "https://challenges.cloudflare.com";

    private static final Pattern HEADER =
            Pattern.compile("add_header\\s+Content-Security-Policy\\s+\"([^\"]+)\"");

    @Test
    void theWidgetTurnstileServesIsAllowedToLoad() {
        Map<String, String> policy = frontendPolicy();

        assertThat(policy.get("script-src"))
                .as("turnstile/v0/api.js is fetched by the page, so script-src decides")
                .contains(TURNSTILE);
        assertThat(policy.get("frame-src"))
                .as("the challenge itself is an iframe, and frame-src does not "
                        + "inherit from script-src -- only from default-src, "
                        + "which names no host")
                .contains(TURNSTILE);
    }

    /**
     * The rest of the policy is still the specification's. Naming one host is
     * a hole the size of that host; naming it by widening {@code default-src}
     * would be a hole the size of the web.
     */
    @Test
    void nothingElseWasWidenedToMakeRoom() {
        Map<String, String> policy = frontendPolicy();

        assertThat(policy.get("default-src")).isEqualTo("'self'");
        assertThat(policy.get("connect-src")).isEqualTo("'self'");
        assertThat(policy.get("style-src")).doesNotContain("http");
        assertThat(policy).containsKey("img-src");
    }

    /**
     * The other half: the warm-up has a URL under {@code /api/v1} and the
     * {@code /api/} block proxies everything under it. Without an exact match
     * ahead of that block, an operational lever meant for the host is a way
     * for anyone to make the server do work for nothing.
     */
    @Test
    void thewarmUpIsNotReachableThroughNginx() {
        assertThat(read(NGINX))
                .as("/api/v1/warmup is kept off the public route")
                .contains("location = /api/v1/warmup");
    }

    /** Directive name to the rest of its line, from the one header nginx sets. */
    private static Map<String, String> frontendPolicy() {
        Matcher header = HEADER.matcher(read(NGINX));
        assertThat(header.find())
                .as("nginx.conf sets no Content-Security-Policy at all")
                .isTrue();

        Map<String, String> directives = new LinkedHashMap<>();
        for (String directive : header.group(1).split(";")) {
            String trimmed = directive.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int space = trimmed.indexOf(' ');
            directives.put(
                    space < 0 ? trimmed : trimmed.substring(0, space),
                    space < 0 ? "" : trimmed.substring(space + 1).trim());
        }
        return directives;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
