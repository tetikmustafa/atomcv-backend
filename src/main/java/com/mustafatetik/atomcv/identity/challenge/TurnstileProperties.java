package com.mustafatetik.atomcv.identity.challenge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The secret half of the Turnstile pair, and where to send it.
 *
 * <p>The site key is the frontend's and is public by design; nothing here
 * needs it.
 *
 * @param secretKey  from the Cloudflare dashboard. Absent everywhere but
 *                   production, where {@link ChallengeConfig} refuses to start
 *                   without it
 * @param verifyUrl  the siteverify endpoint, overridable so a test can point
 *                   it at a socket it controls rather than at the internet
 */
@ConfigurationProperties(prefix = "atomcv.turnstile")
public record TurnstileProperties(String secretKey, String verifyUrl) {

    private static final String SITEVERIFY =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    public TurnstileProperties {
        secretKey = secretKey == null ? "" : secretKey.trim();
        verifyUrl = verifyUrl == null || verifyUrl.isBlank() ? SITEVERIFY : verifyUrl.trim();
    }

    public boolean configured() {
        return !secretKey.isEmpty();
    }
}
