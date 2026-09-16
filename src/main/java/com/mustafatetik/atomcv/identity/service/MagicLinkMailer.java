package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.email.EmailSender;
import com.mustafatetik.atomcv.email.EmailSuppressions;
import com.mustafatetik.atomcv.email.MagicLinkEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The sign-in email, and the one place that decides whether it goes out.
 *
 * <p><strong>A bean of its own so that the class holding the transactions does
 * not also make network calls.</strong> Redeeming a link is transactional and
 * should stay that way — it is four writes that have to settle together — and
 * a class carrying a transactional method is a class where the next send is
 * one refactor away from being inside one. Splitting them is what makes the
 * architecture rule able to say so.
 */
@Service
public class MagicLinkMailer {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkMailer.class);

    private final EmailSender email;
    private final EmailSuppressions suppressions;

    MagicLinkMailer(EmailSender email, EmailSuppressions suppressions) {
        this.email = email;
        this.suppressions = suppressions;
    }

    /**
     * Sends the link, unless this address has told us to stop.
     *
     * <p>Returns nothing, and says nothing to its caller about what happened.
     * The silence is the enumeration defence: the token was written either way
     * and the work is the same either way, so there is nothing here for a
     * caller to notice or report.
     */
    public void sendLink(String address, String locale, String link, int validForMinutes) {
        if (suppressions.isSuppressed(address)) {
            // A hard bounce or a complaint is a standing instruction, and
            // sending anyway costs the domain's reputation — which would break
            // sign-in for everyone, not for this address.
            log.info("Skipped a sign-in email to a suppressed address");
            return;
        }
        if (!email.send(MagicLinkEmail.to(address, locale, link, validForMinutes))) {
            // Said, but not to the caller: a failure that reached the response
            // would answer the question the silence exists to leave
            // unanswered.
            log.warn("A sign-in email was not accepted by the sender");
        }
    }
}
