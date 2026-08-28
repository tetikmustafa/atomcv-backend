package com.mustafatetik.atomcv.email;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The endpoint secret, and the one decision that goes with it.
 *
 * <p><strong>No secret means every delivery is refused</strong>, and that is
 * the safe direction rather than the convenient one. The alternative — waving
 * unsigned deliveries through when nothing is configured — would mean a
 * deployment that forgot the secret accepts anything anyone posts, and
 * suppressing an address stops that person signing in. A webhook that is
 * refused is a webhook Resend retries and eventually shows as failing in a
 * dashboard; that is a visible problem. The other way round is a silent one.
 *
 * <p>Locally there is no secret and no Resend, so nothing arrives to refuse.
 */
@Component
@ConfigurationProperties(prefix = "atomcv.email.webhook")
public class EmailWebhooks {

    private static final Logger log = LoggerFactory.getLogger(EmailWebhooks.class);

    private String secret = "";

    public void setSecret(String secret) {
        this.secret = secret == null ? "" : secret.trim();
    }

    public String getSecret() {
        return secret;
    }

    public boolean verify(String id, String timestamp, String signature, String body, Instant now) {
        if (secret.isEmpty()) {
            log.warn("A webhook delivery arrived and no endpoint secret is configured; "
                    + "refusing it. Set RESEND_WEBHOOK_SECRET.");
            return false;
        }
        return ResendSignature.valid(secret, id, timestamp, signature, body, now);
    }
}
