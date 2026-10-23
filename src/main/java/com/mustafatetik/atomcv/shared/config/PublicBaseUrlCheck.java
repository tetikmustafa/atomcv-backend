package com.mustafatetik.atomcv.shared.config;

import java.net.URI;
import java.util.Locale;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Refuses to start production with a public address that is not one.
 *
 * <p>{@code APP_BASE_URL} is where every link this server writes points: the
 * magic link, the unsubscribe link in each lifecycle email, and the OAuth
 * callback. Unset, it falls back to {@code http://localhost:3000} — right for
 * local development, and in production a deployment that <em>works
 * perfectly</em> while every email it sends links to the reader's own
 * machine. Nobody finds that from the behaviour; the first person who would
 * is a user who cannot sign in.
 *
 * <p>{@code ChallengeConfig}'s shape: a refusal at bean creation, before the
 * port opens, under {@code prod} only. HTTPS is required as well, because the
 * session cookie is {@code Secure} and a link to plain HTTP lands somewhere
 * the cookie is never sent.
 */
@Configuration
public class PublicBaseUrlCheck {

    static final String PROPERTY = "APP_BASE_URL";

    public PublicBaseUrlCheck(Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        String problem = problemWith(environment.getProperty(PROPERTY));
        if (problem != null) {
            throw new IllegalStateException(PROPERTY + " is required in production and "
                    + problem + ": every magic link, unsubscribe link and OAuth callback "
                    + "is built from it.");
        }
    }

    /** @return why this address cannot be production's, or null when it can */
    static String problemWith(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "is not set";
        }
        URI uri;
        try {
            uri = URI.create(baseUrl.strip());
        } catch (IllegalArgumentException malformed) {
            return "is not a URL";
        }
        if (!"https".equals(uri.getScheme())) {
            return "must be https";
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || host.equals("localhost") || host.startsWith("127.")
                || host.equals("0.0.0.0") || host.endsWith(".localhost")) {
            return "must name the public host, not " + (host.isEmpty() ? "nothing" : host);
        }
        return null;
    }
}
