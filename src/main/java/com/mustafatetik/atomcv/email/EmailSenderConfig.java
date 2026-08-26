package com.mustafatetik.atomcv.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Which sender this deployment got, decided by whether it has a Resend key.
 *
 * <p>The same rule as Bolum 27.3's providers: a missing key is a configuration
 * fact rather than a failure, and no deployment has to declare what it is not
 * using. One bean method rather than two conditional ones, so the choice is a
 * branch that can be read and tested instead of an ordering between
 * annotations.
 */
@Configuration
public class EmailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderConfig.class);

    @Bean
    EmailSender emailSender(EmailProperties properties, ObjectMapper json,
            ObjectProvider<JavaMailSender> mail) {
        if (properties.hasResendKey()) {
            log.info("Sending email through Resend");
            return new ResendEmailSender(properties, json);
        }
        JavaMailSender smtp = mail.getIfAvailable();
        if (smtp != null) {
            // INFO and not WARN: this is the intended state locally, and a
            // warning that fires on every developer's machine is one nobody
            // reads by the time it matters.
            log.info("No Resend key configured; sending email over SMTP");
            return new SmtpEmailSender(smtp, properties);
        }
        // Neither. Boot builds no JavaMailSender unless spring.mail is set, so
        // this is a deployment that can accept a sign-in request and never act
        // on it. Said loudly at startup rather than discovered by the first
        // person who never got their link -- the same reason LlmPricingAudit
        // names an unpriced model instead of costing it at zero.
        log.warn("No email sender configured: sign-in links will not be delivered. "
                + "Set RESEND_API_KEY, or spring.mail.host for SMTP.");
        return message -> false;
    }
}
