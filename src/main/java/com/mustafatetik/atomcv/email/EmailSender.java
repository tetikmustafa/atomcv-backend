package com.mustafatetik.atomcv.email;

/**
 * How a message leaves the building.
 *
 * <p>One interface, two implementations chosen by configuration: Resend where
 * there is an API key, SMTP everywhere else — which in development means
 * Mailpit, so the email is read the way a person reads it rather than
 * inspected as a log line.
 *
 * <p><strong>Failure is reported, never thrown.</strong> The one caller is
 * Bolum 40.2's magic link, and Bolum 40.4 requires its response to be the same
 * whatever happens; an exception escaping here would change that response and
 * hand an attacker the difference.
 */
public interface EmailSender {

    /**
     * @return whether the provider accepted it. False is not "the person did
     *         not receive it" — delivery is decided later and elsewhere — only
     *         that handing it over failed
     */
    boolean send(EmailMessage message);
}
