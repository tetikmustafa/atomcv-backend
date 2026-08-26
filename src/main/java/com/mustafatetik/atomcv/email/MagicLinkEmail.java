package com.mustafatetik.atomcv.email;

import java.util.Locale;

/**
 * The one email this product sends so far (Bolum 40.2).
 *
 * <p>Prose in the backend, which the error catalogue is careful never to do —
 * and the difference is real. An error is read inside a client that already
 * knows the user's language and can translate a code; an email is read in an
 * inbox with no client of ours anywhere near it, so the sentence has to be
 * written before it leaves. {@code users.locale} decides which.
 *
 * <p>Both parts, always. A text-only message is filtered more often, and an
 * HTML-only one is unreadable in the clients that refuse HTML — the same
 * corporate gateways Bolum 40.3 warns about for prefetching.
 *
 * <p><strong>The link is a credential.</strong> It is interpolated once, into
 * the body, and appears in no log line anywhere in this package.
 */
public final class MagicLinkEmail {

    private MagicLinkEmail() {
    }

    public static EmailMessage to(String recipient, String locale, String url, int minutes) {
        return isTurkish(locale) ? turkish(recipient, url, minutes)
                : english(recipient, url, minutes);
    }

    /**
     * Absolute rule 7: without {@code Locale.ROOT} this comparison is itself
     * locale-dependent, which is a particularly silly way to get the Turkish
     * branch wrong.
     */
    private static boolean isTurkish(String locale) {
        return locale != null && locale.toLowerCase(Locale.ROOT).startsWith("tr");
    }

    private static EmailMessage turkish(String recipient, String url, int minutes) {
        String subject = "AtomCV giriş bağlantınız";
        String text = """
                Merhaba,

                AtomCV'ye giriş yapmak için bu bağlantıyı açın:

                %s

                Bağlantı %d dakika geçerli ve yalnızca bir kez kullanılabilir.

                Bu isteği siz yapmadıysanız yapmanız gereken bir şey yok;
                bağlantı kullanılmadan süresi dolacak.
                """.formatted(url, minutes);
        String html = html(
                "AtomCV'ye giriş yapın",
                "Aşağıdaki düğme sizi hesabınıza götürür.",
                "Giriş yap",
                url,
                "Bağlantı " + minutes
                        + " dakika geçerli ve yalnızca bir kez kullanılabilir. "
                        + "Bu isteği siz yapmadıysanız yapmanız gereken bir şey yok.");
        return new EmailMessage(recipient, subject, text, html);
    }

    private static EmailMessage english(String recipient, String url, int minutes) {
        String subject = "Your AtomCV sign-in link";
        String text = """
                Hello,

                Open this link to sign in to AtomCV:

                %s

                The link is valid for %d minutes and can be used once.

                If you did not ask for this, there is nothing to do; the link
                will expire unused.
                """.formatted(url, minutes);
        String html = html(
                "Sign in to AtomCV",
                "The button below takes you to your account.",
                "Sign in",
                url,
                "The link is valid for " + minutes + " minutes and can be used once. "
                        + "If you did not ask for this, there is nothing to do.");
        return new EmailMessage(recipient, subject, text, html);
    }

    /**
     * Inline styles and a table-free layout. Every rule an email client
     * respects has to travel in the attribute; a stylesheet is stripped, and
     * a class is meaningless by the time it arrives.
     */
    private static String html(
            String heading, String lead, String button, String url, String footer) {
        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;\
                max-width:520px;margin:0 auto;padding:32px 24px;color:#16202b;line-height:1.6">
                  <h1 style="font-size:20px;margin:0 0 12px">%s</h1>
                  <p style="margin:0 0 24px;color:#4e6072">%s</p>
                  <p style="margin:0 0 28px">
                    <a href="%s" style="display:inline-block;background:#16202b;color:#ffffff;\
                text-decoration:none;padding:12px 22px;border-radius:4px;font-weight:600">%s</a>
                  </p>
                  <p style="margin:0 0 8px;font-size:13px;color:#7a8b9b">%s</p>
                  <p style="margin:0;font-size:12px;color:#7a8b9b;word-break:break-all">%s</p>
                </div>
                """.formatted(heading, lead, url, button, footer, url);
    }
}
