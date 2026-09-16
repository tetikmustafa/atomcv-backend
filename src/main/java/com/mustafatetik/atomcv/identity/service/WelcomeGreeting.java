package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.email.EmailProperties;
import com.mustafatetik.atomcv.email.EmailSender;
import com.mustafatetik.atomcv.email.EmailSuppressions;
import com.mustafatetik.atomcv.email.WelcomeEmail;
import com.mustafatetik.atomcv.identity.domain.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The welcome, sent once and from one place.
 *
 * <p><strong>The trigger is the first successful sign-in, not the first
 * row.</strong> A {@code users} row is written the moment anyone types an
 * address into the sign-in box — before that person has proved anything, and
 * whether or not the address is theirs. Greeting on row creation would have
 * sent mail to every address anybody cared to enter, which is the abuse the
 * limits exist to slow down.
 *
 * <p>So the question is {@code last_seen_at}, which is null exactly until the
 * first session exists — and it is asked by the two routes rather than here,
 * because both call {@code seen(...)} immediately before creating a session
 * and it stops being answerable the moment they do.
 *
 * <p><strong>Reached by an event after the commit, and it used to be reached
 * by a call inside one</strong> (denetim, beşinci tur). The note here said
 * "not transactional, deliberately" and was describing its own annotation
 * rather than the transaction it actually ran in: both callers are
 * {@code @Transactional}, so a Resend round trip happened holding a connection
 * from a pool of ten — on the one request every user makes before they can
 * make any other. <strong>A class that opens no transaction is not a class
 * that runs outside one.</strong>
 *
 * <p>Which also fixed the asymmetry the old note claimed as deliberate: the
 * trigger is now the commit rather than the attempt, so a sign-in that rolled
 * back no longer sends a welcome for a session nobody got.
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
     * The welcome itself, once somebody else has decided it is the first time.
     *
     * <p><strong>"Is this a first sign-in" is no longer asked here</strong>,
     * and it cannot be: by the time this runs the transaction has committed
     * and {@code seen(...)} has filled in the very field that answer reads.
     * The two routes ask it in the one moment it is answerable and publish
     * {@link FirstSignIn} only then — so what is left here is the other
     * question, whether this address may be written to at all.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFirstSignIn(FirstSignIn event) {
        greet(event.account());
    }

    /**
     * @param account the person who has just arrived for the first time
     */
    public void greet(UserAccount account) {
        if (!account.wantsLifecycleEmails()) {
            // Reachable: somebody can unsubscribe from the welcome they were
            // sent, and then sign in for the first time on another device
            // before the first session was ever created. Rare, and the
            // preference means what it says.
            return;
        }
        if (suppressions.isSuppressed(account.getEmail())) {
            // A hard bounce is a standing instruction and outranks the
            // preference: sending anyway spends the domain's reputation, which
            // breaks sign-in for everybody else.
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
