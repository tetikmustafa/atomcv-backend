package com.mustafatetik.atomcv.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Who the mail comes from, and the key that decides how it goes.
 *
 * @param from      the envelope sender. It has to live under the verified
 *                  subdomain (Adim 3.2) — SPF and DKIM are published there and
 *                  nowhere else, so a From on the apex fails both
 * @param fromName  what an inbox shows instead of the address
 * @param replyTo   optional; absent means replies go to {@code from}
 * @param resendKey empty outside production. Its presence is what selects the
 *                  Resend sender, on the same principle as Bolum 27.3's
 *                  providers: no key is a configuration fact, not a failure
 */
@ConfigurationProperties(prefix = "atomcv.email")
public record EmailProperties(
        String from, String fromName, String replyTo, String resendKey, String unsubscribeUrl) {

    public EmailProperties {
        from = from == null || from.isBlank() ? "no-reply@localhost" : from;
        fromName = fromName == null || fromName.isBlank() ? "AtomCV" : fromName;
        replyTo = replyTo == null || replyTo.isBlank() ? null : replyTo;
        resendKey = resendKey == null || resendKey.isBlank() ? null : resendKey;
        unsubscribeUrl = unsubscribeUrl == null || unsubscribeUrl.isBlank()
                ? "http://localhost:3000/unsubscribe"
                : unsubscribeUrl.replaceAll("/+$", "");
    }

    /**
     * Where a person lands to turn the optional post off (Bolum 57.7).
     *
     * <p>A page on the frontend rather than an endpoint here, and Bolum 40.3
     * is the reason: a gateway that fetches every link in a message would
     * unsubscribe somebody who never clicked. The page carries the button.
     */
    public String unsubscribeLinkFor(java.util.UUID token) {
        return unsubscribeUrl + "?t=" + token;
    }

    public boolean hasResendKey() {
        return resendKey != null;
    }

    /** {@code AtomCV <no-reply@mail.example.com>}. */
    public String fromHeader() {
        return fromName + " <" + from + ">";
    }
}
