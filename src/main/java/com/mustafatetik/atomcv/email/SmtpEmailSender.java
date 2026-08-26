package com.mustafatetik.atomcv.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Everywhere there is no Resend key — which in practice means development,
 * pointed at Mailpit.
 *
 * <p>Mailpit rather than a sender that logs "an email would have gone here":
 * Bolum 40.2's link is the first thing anyone who does not use a provider ever
 * sees of this product, and a subject line, a rendered button and a link that
 * actually resolves are things you have to look at to get right. The Stage 0
 * checklist already keeps that interface open at {@code :8025}.
 */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mail;
    private final EmailProperties properties;

    public SmtpEmailSender(JavaMailSender mail, EmailProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    @Override
    public boolean send(EmailMessage message) {
        try {
            MimeMessage mime = mail.createMimeMessage();
            // Multipart, so both the plain part and the HTML part go.
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(properties.from(), properties.fromName());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            if (properties.replyTo() != null) {
                helper.setReplyTo(properties.replyTo());
            }
            mail.send(mime);
            return true;
        } catch (Exception refused) {
            // No recipient in the line: the address is the one thing an
            // enumeration attempt wants to see confirmed.
            log.warn("SMTP refused a message: {}", refused.getClass().getSimpleName());
            return false;
        }
    }
}
