package com.mustafatetik.atomcv.email;

import java.util.Locale;

/**
 * What is left to say once there is nothing left to say it about
 * (Bolum 57.4, 57.7).
 *
 * <p><strong>Transactional, and the one lifecycle email nobody can switch
 * off.</strong> Bolum 57.4 requires a person to be told their data is gone;
 * a preference that could suppress this would be a way of not telling them.
 *
 * <p>No button and no link. Every other message this product sends ends in
 * somewhere to go, and this one deliberately does not: the account it would
 * lead to is what was just deleted.
 *
 * <p>Absolute rule 4 is easy to keep here and worth saying anyway — the
 * message names nothing that was in the account, because by the time it is
 * written there is nothing to name.
 */
public final class AccountDeletedEmail {

    private AccountDeletedEmail() {
    }

    public static EmailMessage to(String recipient, String locale) {
        return isTurkish(locale) ? turkish(recipient) : english(recipient);
    }

    /** Absolute rule 7: the Turkish locale lowercases "I" into something else. */
    private static boolean isTurkish(String locale) {
        return locale != null && locale.toLowerCase(Locale.ROOT).startsWith("tr");
    }

    private static EmailMessage turkish(String recipient) {
        String subject = "AtomCV hesabınız silindi";
        String text = """
                Merhaba,

                AtomCV hesabınız ve ona bağlı her şey silindi: profiliniz,
                ürettiğiniz CV'ler ve oturumlarınız.

                Bu geri alınamaz ve yapmanız gereken bir şey yok. Bu son
                e-posta; adresinize başka bir şey göndermiyoruz.

                Yeniden kullanmak isterseniz aynı adresle sıfırdan
                başlayabilirsiniz.
                """;
        String html = html(
                "Hesabınız silindi",
                "AtomCV hesabınız ve ona bağlı her şey silindi: profiliniz, "
                        + "ürettiğiniz CV'ler ve oturumlarınız.",
                "Bu geri alınamaz ve yapmanız gereken bir şey yok. Bu son e-posta; "
                        + "adresinize başka bir şey göndermiyoruz.");
        return new EmailMessage(recipient, subject, text, html);
    }

    private static EmailMessage english(String recipient) {
        String subject = "Your AtomCV account has been deleted";
        String text = """
                Hello,

                Your AtomCV account and everything attached to it has been
                deleted: your profile, the CVs you generated, and your sessions.

                This cannot be undone and there is nothing for you to do. This
                is the last email; we are not sending anything else to this
                address.

                If you want to use AtomCV again you can start over with the
                same address.
                """;
        String html = html(
                "Your account has been deleted",
                "Your AtomCV account and everything attached to it has been deleted: "
                        + "your profile, the CVs you generated, and your sessions.",
                "This cannot be undone and there is nothing for you to do. This is the "
                        + "last email; we are not sending anything else to this address.");
        return new EmailMessage(recipient, subject, text, html);
    }

    /**
     * {@link MagicLinkEmail}'s layout without the button, and inline for the
     * same reason: a stylesheet is stripped before it arrives and a class name
     * means nothing by then.
     */
    private static String html(String heading, String lead, String footer) {
        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;\
                max-width:520px;margin:0 auto;padding:32px 24px;color:#16202b;line-height:1.6">
                  <h1 style="font-size:20px;margin:0 0 12px">%s</h1>
                  <p style="margin:0 0 24px;color:#4e6072">%s</p>
                  <p style="margin:0;font-size:13px;color:#7a8b9b">%s</p>
                </div>
                """.formatted(heading, lead, footer);
    }
}
