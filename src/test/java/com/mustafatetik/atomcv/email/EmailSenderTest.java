package com.mustafatetik.atomcv.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Both senders, exercised.
 *
 * <p>Neither runs anywhere else in the suite — the magic link tests replace
 * the sender with one that records — and CLAUDE.md has the answer to that: a
 * component the whole suite switches off has unverified wiring. The Resend one
 * is the production path and the SMTP one is what every developer sees, so
 * "it compiles" is not enough for either.
 */
class EmailSenderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final EmailMessage MESSAGE = new EmailMessage(
            "ada@example.com", "Your AtomCV sign-in link", "a link", "<p>a link</p>");

    private static final EmailProperties PROPERTIES = new EmailProperties(
            "no-reply@mail.example.com", "AtomCV", "hello@example.com", "re_a-key");

    // ── Resend ────────────────────────────────────────────────────────────

    private HttpServer resend;
    private final List<String> bodies = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();
    private int status = 200;

    @BeforeEach
    void startStub() throws IOException {
        resend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        resend.createContext("/emails", this::record);
        resend.start();
    }

    @AfterEach
    void stopStub() {
        resend.stop(0);
    }

    private void record(HttpExchange exchange) throws IOException {
        bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] body = "{\"id\": \"a-message-id\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Test
    void resendSendsTheShapeItsApiExpects() throws Exception {
        assertThat(resendSender().send(MESSAGE)).isTrue();

        JsonNode payload = JSON.readTree(bodies.get(0));
        assertThat(payload.path("from").asText()).isEqualTo("AtomCV <no-reply@mail.example.com>");
        assertThat(payload.path("to").get(0).asText()).isEqualTo("ada@example.com");
        assertThat(payload.path("subject").asText()).isEqualTo("Your AtomCV sign-in link");
        // Both parts, always: a text-only message is filtered more often and
        // an HTML-only one is unreadable in the clients that refuse HTML.
        assertThat(payload.path("text").asText()).isEqualTo("a link");
        assertThat(payload.path("html").asText()).isEqualTo("<p>a link</p>");
        assertThat(payload.path("reply_to").asText()).isEqualTo("hello@example.com");
        assertThat(authorizations.get(0)).isEqualTo("Bearer re_a-key");
    }

    @Test
    void withoutAReplyToTheFieldIsLeftOutRatherThanSentEmpty() throws Exception {
        var noReplyTo = new EmailProperties("no-reply@mail.example.com", "AtomCV", null, "re_k");

        new ResendEmailSender(noReplyTo, JSON, endpoint()).send(MESSAGE);

        assertThat(JSON.readTree(bodies.get(0)).has("reply_to")).isFalse();
    }

    /**
     * A refusal is reported, never thrown. Bolum 40.4 needs the magic link's
     * answer to be the same whatever happened, and an exception escaping here
     * would change it.
     */
    @Test
    void aRefusedSendIsFalseAndNotAnException() {
        status = 422;

        assertThat(resendSender().send(MESSAGE)).isFalse();
    }

    @Test
    void anUnreachableProviderIsFalseAndNotAnException() {
        var pointingNowhere = new ResendEmailSender(
                PROPERTIES, JSON, "http://127.0.0.1:1/emails");

        assertThat(pointingNowhere.send(MESSAGE)).isFalse();
    }

    // ── SMTP ──────────────────────────────────────────────────────────────

    /**
     * The MimeMessage is built for real; only the socket is stubbed. That is
     * where the mistakes live — {@code setFrom} with a display name throws on
     * a bad encoding, and the multipart flag decides whether the HTML part
     * exists at all.
     */
    @Test
    void smtpBuildsAMessageWithBothPartsAndTheRightHeaders() throws Exception {
        var mail = spy(new JavaMailSenderImpl());
        doNothing().when(mail).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

        assertThat(new SmtpEmailSender(mail, PROPERTIES).send(MESSAGE)).isTrue();

        var captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        org.mockito.Mockito.verify(mail).send(captor.capture());
        MimeMessage mime = captor.getValue();
        assertThat(mime.getSubject()).isEqualTo("Your AtomCV sign-in link");
        assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("ada@example.com");
        assertThat(mime.getFrom()[0].toString()).contains("no-reply@mail.example.com");
        // saveChanges() is what writes the Content-Type header, and send() is
        // what normally calls it -- which is the call this test stubbed out.
        mime.saveChanges();
        assertThat(mime.getContentType()).startsWith("multipart/");
        assertThat(mime.getContent()).isInstanceOf(jakarta.mail.internet.MimeMultipart.class);
    }

    @Test
    void anSmtpFailureIsFalseAndNotAnException() {
        var mail = spy(new JavaMailSenderImpl());
        doThrow(new MailSendException("no route"))
                .when(mail).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

        assertThat(new SmtpEmailSender(mail, PROPERTIES).send(MESSAGE)).isFalse();
    }

    // ── selection ─────────────────────────────────────────────────────────

    @Test
    void theKeyDecidesWhichSenderADeploymentGets() {
        var withKey = new EmailProperties("a@b.c", "AtomCV", null, "re_a-key");
        var withoutKey = new EmailProperties("a@b.c", "AtomCV", null, "  ");

        assertThat(withKey.hasResendKey()).isTrue();
        assertThat(withoutKey.hasResendKey()).isFalse();
        assertThat(withKey.fromHeader()).isEqualTo("AtomCV <a@b.c>");
    }

    private ResendEmailSender resendSender() {
        return new ResendEmailSender(PROPERTIES, JSON, endpoint());
    }

    private String endpoint() {
        return "http://127.0.0.1:" + resend.getAddress().getPort() + "/emails";
    }
}
