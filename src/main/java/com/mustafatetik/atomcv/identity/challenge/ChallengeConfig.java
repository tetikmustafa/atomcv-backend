package com.mustafatetik.atomcv.identity.challenge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Which challenge this deployment got, decided by whether it has a secret.
 *
 * <p>{@code EmailSenderConfig}'s shape, plus a door it does not need. A
 * deployment with no Resend key sends nothing, which is visible the moment
 * somebody waits for a link; a deployment with no challenge secret <em>works
 * perfectly</em> and is open. So the missing secret is a warning locally and a
 * refusal to start in production — the one place where nobody would ever find
 * out from the behaviour.
 *
 * <p>Refused at bean creation and not at {@code ApplicationReadyEvent}: by the
 * time that fires the port is open, and an instance that has already served a
 * request is not one that failed to start.
 */
@Configuration
public class ChallengeConfig {

    private static final Logger log = LoggerFactory.getLogger(ChallengeConfig.class);

    @Bean
    Challenge challenge(TurnstileProperties properties, ObjectMapper json,
            Environment environment) {
        if (properties.configured()) {
            log.info("Sign-in requests are challenged through Turnstile");
            return new TurnstileChallenge(properties, json);
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "TURNSTILE_SECRET_KEY is required in production: Bolum 40.4.1 makes "
                            + "the challenge half of what bounds an endpoint that creates a "
                            + "user row and sends mail for any address anyone types.");
        }
        // Local development and the test suite. Said, rather than silently
        // waved through, so a deployment that ends up here by accident is not
        // discovered by reading the traffic.
        log.warn("No challenge configured: sign-in requests are not checked for a person. "
                + "Set TURNSTILE_SECRET_KEY outside local development.");
        return token -> true;
    }
}
