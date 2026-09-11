package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.email.EmailProperties;
import com.mustafatetik.atomcv.email.EmailSender;
import com.mustafatetik.atomcv.email.EmailSuppressions;
import com.mustafatetik.atomcv.email.WelcomeEmail;
import com.mustafatetik.atomcv.identity.domain.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Bolum 57.7's welcome, sent once and from one place.
 *
 * <p><strong>The trigger is the first successful sign-in, not the first
 * row.</strong> Bolum 40.4 writes a {@code users} row the moment anyone types
 * an address into the sign-in box — before that person has proved anything,
 * and whether or not the address is theirs. Greeting on row creation would
 * have sent mail to every address anybody cared to enter, which is the abuse
 * Bolum 40.5's limits exist to slow down.
 *
 * <p>So it reads {@code last_seen_at}, which is null exactly until the first
 * session exists. Both routes pass through here for that reason: the magic
 * link's verification and OAuth both call {@code seen(...)} immediately before
 * creating a session, and this has to be asked <em>before</em> they do.
 *
 * <p><strong>Not transactional, deliberately, and the asymmetry with the
 * deletion confirmation is the point.</strong> A rolled-back deletion that
 * had already told somebody their data was gone would be a lie; a welcome for
 * a sign-in that then failed is one extra email. The first is worth carrying
 * an address out of a transaction for, the second is not.
 */
@Service
public class WelcomeGreeting {

    private static final Logger log = LoggerFactory.getLogger(WelcomeGreeting.class);

    private final EmailSender email;
    private final EmailSuppressions suppressions;
    private final EmailProperties properties;

    WelcomeGreeting(EmailSender email, EmailSuppressions suppressions,
            EmailProperties properties) {

        this.email = email;
        this.suppressions = suppressions;
        this.properties = properties;
    }

    /**
     * @param account read before {@code seen(...)} has touched it, or this
     *                greets nobody: the field it asks about is the one that
     *                call is about to fill in
     */
    public void greetIfFirstSignIn(UserAccount account) {
        if (!account.hasNeverSignedIn()) {
            return;
        }
        if (!account.wantsLifecycleEmails()) {
            // Reachable: somebody can unsubscribe from the welcome they were
            // sent, and then sign in for the first time on another device
            // before the first session was ever created. Rare, and the
            // preference means what it says.
            return;
        }
        if (suppressions.isSuppressed(account.getEmail())) {
            // A hard bounce is a standing instruction and outranks the
            // preference (Bolum 57.7): sending anyway spends the domain's
            // reputation, which breaks sign-in for everybody else.
            log.info("Skipped a welcome to a suppressed address");
            return;
        }
        boolean accepted = email.send(WelcomeEmail.to(
                account.getEmail(), account.getLocale(),
                properties.unsubscribeLinkFor(account.getUnsubscribeToken())));
        if (!accepted) {
            // Reported, never thrown: a welcome that did not leave is not a
            // reason to refuse somebody their first sign-in.
            log.warn("A welcome email was not accepted by the sender");
        }
    }
}
