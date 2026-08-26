package com.mustafatetik.atomcv.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production's sender (Bolum 2's table).
 *
 * <p>The HTTP API rather than Resend's SMTP, which it also offers: a refused
 * send comes back as a status and a named error instead of a bounce arriving
 * minutes later at an address nobody reads, and the message id it returns is
 * what a support question about a missing email is answered with.
 *
 * <p><strong>Nothing here logs a recipient or a body.</strong> The magic link
 * is in that body, so a log line carrying it would put a credential in the log
 * — absolute rule 4, and the reason behind it.
 */
public class ResendEmailSender implements EmailSender {

    private static final String ENDPOINT = "https://api.resend.com/emails";

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final EmailProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;
    private final String endpoint;

    ResendEmailSender(EmailProperties properties, ObjectMapper json, String endpoint) {
        this.properties = properties;
        this.json = json;
        this.endpoint = endpoint;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public ResendEmailSender(EmailProperties properties, ObjectMapper json) {
        this(properties, json, ENDPOINT);
    }

    @Override
    public boolean send(EmailMessage message) {
        String body;
        try {
            body = json.writeValueAsString(payloadFor(message));
        } catch (Exception unserialisable) {
            log.warn("Could not build the email payload: {}",
                    unserialisable.getClass().getSimpleName());
            return false;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + properties.resendKey())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // The status alone. An error body echoes the request back, and
                // the request contains the link.
                log.warn("Resend refused a message with http {}", response.statusCode());
                return false;
            }
            return true;
        } catch (IOException unreachable) {
            log.warn("Resend was unreachable: {}", unreachable.getClass().getSimpleName());
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("Sending was interrupted");
            return false;
        }
    }

    private Map<String, Object> payloadFor(EmailMessage message) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("from", properties.fromHeader());
        payload.put("to", new String[] {message.to()});
        payload.put("subject", message.subject());
        payload.put("text", message.text());
        payload.put("html", message.html());
        if (properties.replyTo() != null) {
            payload.put("reply_to", properties.replyTo());
        }
        return payload;
    }
}
