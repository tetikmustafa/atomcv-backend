package com.mustafatetik.atomcv.email;

import java.util.Objects;

/**
 * One message on its way out.
 *
 * <p>Both bodies, always. A text-only email is filtered more often than one
 * with both parts, and an HTML-only email is unreadable in the clients that
 * refuse HTML — which includes several corporate gateways, the same ones
 * Bolum 40.3 already warns about for a different reason.
 *
 * @param to      the recipient. User content in the sense that matters:
 *                absolute rule 4 keeps it out of every log line here
 * @param subject what the person sees in their inbox list
 * @param text    the plain part
 * @param html    the rich part
 */
public record EmailMessage(String to, String subject, String text, String html) {

    public EmailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(html, "html");
        if (to.isBlank()) {
            throw new IllegalArgumentException("to must not be blank");
        }
    }
}
