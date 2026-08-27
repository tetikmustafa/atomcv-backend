package com.mustafatetik.atomcv.identity.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bolum 40.5's three numbers, and the reason each one is the size it is.
 *
 * <p>Configurable because the right values are the deployment's to know: the
 * global layer is a guess at the sending provider's tolerance, and a guess
 * that needs a release to correct is a guess nobody corrects.
 *
 * @param perEmail one address, so a person who mistypes theirs can try again a
 *                 few times and an attacker cannot bury a real inbox
 * @param perIp    one caller, which is the layer a script actually meets
 * @param global   the whole deployment, protecting the sending domain's
 *                 reputation rather than any one user — a burst of undeliverable
 *                 mail costs everybody's sign-in, not the sender's
 */
@ConfigurationProperties(prefix = "atomcv.rate-limit.sign-in")
public record RateLimitProperties(Layer perEmail, Layer perIp, Layer global) {

    public RateLimitProperties {
        perEmail = perEmail == null ? new Layer(3, Duration.ofMinutes(15)) : perEmail;
        perIp = perIp == null ? new Layer(10, Duration.ofHours(1)) : perIp;
        global = global == null ? new Layer(200, Duration.ofHours(1)) : global;
    }

    /**
     * @param limit  how many requests the window admits
     * @param window how far back the window reaches
     */
    public record Layer(int limit, Duration window) {

        public Layer {
            if (limit < 1 || window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException(
                        "a layer admits at least one request over a positive window");
            }
        }
    }
}
