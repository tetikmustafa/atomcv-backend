package com.mustafatetik.atomcv.email.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.email.EmailSuppressions;
import com.mustafatetik.atomcv.email.EmailWebhooks;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What Resend tells us about mail we sent (Bolum 40.2, § 55's suppression list).
 *
 * <p>{@code EmailSuppressions} could read the table and nothing wrote it. This
 * is the writer. A hard bounce or a complaint is a standing instruction, and
 * continuing to send after one is how a sending domain loses its reputation —
 * which for the magic link means the product stops working for everyone, not
 * just for the address that bounced.
 *
 * <p><strong>Unauthenticated by necessity and signed by requirement.</strong> A
 * webhook carries no session, so the signature is the whole of the security
 * here: anything believed at this endpoint can suppress an address, and a
 * suppressed address cannot sign in. See {@code ResendSignature}.
 *
 * <p><strong>Always 200, whatever happens.</strong> A webhook that answers
 * anything else is retried, and every non-2xx also counts against the endpoint
 * at the provider until it is disabled — so an event type we do not handle, or
 * a payload we cannot read, must be accepted and dropped rather than argued
 * with. The one exception is a bad signature, which is a 401 because it is not
 * Resend on the other end.
 *
 * <p>Hidden from the published schema: this is Resend's contract, not ours, and
 * the frontend generates its types from that document.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/webhooks/resend")
public class ResendWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ResendWebhookController.class);

    private final EmailWebhooks properties;
    private final EmailSuppressions suppressions;
    private final ObjectMapper json;
    private final Clock clock;

    ResendWebhookController(EmailWebhooks properties, EmailSuppressions suppressions,
            ObjectMapper json, Clock clock) {
        this.properties = properties;
        this.suppressions = suppressions;
        this.json = json;
        this.clock = clock;
    }

    /**
     * @param body the raw JSON, and it has to be raw: the signature covers the
     *             bytes that arrived, and a body Spring parsed and re-serialised
     *             hashes differently
     */
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "svix-id", required = false) String id,
            @RequestHeader(value = "svix-timestamp", required = false) String timestamp,
            @RequestHeader(value = "svix-signature", required = false) String signature,
            @RequestBody(required = false) String body) {

        if (!properties.verify(id, timestamp, signature, body, clock.instant())) {
            // No detail. Telling an unsigned caller *why* it was refused helps
            // them get it right on the next attempt.
            log.warn("A webhook delivery was refused: the signature did not verify");
            return ResponseEntity.status(401).build();
        }

        try {
            handle(json.readTree(body));
        } catch (Exception unreadable) {
            // Accepted and dropped. Arguing with a payload we cannot read only
            // buys the same payload again, four more times.
            log.warn("A verified webhook delivery could not be read: {}",
                    unreadable.getClass().getSimpleName());
        }
        return ResponseEntity.ok().build();
    }

    private void handle(JsonNode event) {
        String type = event.path("type").asText("");
        String email = firstRecipient(event);
        if (email.isBlank()) {
            return;
        }

        switch (type) {
            case "email.bounced" -> {
                // Only a permanent one. A transient bounce is a full mailbox or
                // a greylist, and suppressing for it would lock somebody out of
                // their own account over a server that was busy for an hour.
                String kind = event.path("data").path("bounce").path("type")
                        .asText("").toLowerCase(Locale.ROOT);
                if (kind.startsWith("permanent") || kind.isEmpty()) {
                    suppress(email, EmailSuppressions.Reason.HARD_BOUNCE, type);
                } else {
                    log.info("A transient bounce was not suppressed");
                }
            }
            case "email.complained" ->
                    suppress(email, EmailSuppressions.Reason.COMPLAINT, type);
            // delivered, opened, clicked and the rest are somebody else's
            // feature. Accepted, counted by the 200, and dropped.
            default -> log.debug("Webhook event {} needs no action", type);
        }
    }

    /**
     * The address, and it never reaches a log line. Absolute rule 4 covers an
     * email address as squarely as it covers a CV: this method is the only
     * place one is read out of the payload, and what is logged is the event.
     */
    private void suppress(String email, EmailSuppressions.Reason reason, String type) {
        suppressions.suppress(email, reason);
        log.info("Suppressed an address after {}", type);
    }

    private static String firstRecipient(JsonNode event) {
        JsonNode to = event.path("data").path("to");
        if (to.isArray() && !to.isEmpty()) {
            return to.get(0).asText("");
        }
        return to.isTextual() ? to.asText("") : "";
    }
}
