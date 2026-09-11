package com.mustafatetik.atomcv.email;

import java.util.Locale;

/**
 * The first thing this product says to somebody who has just arrived
 * (Bolum 57.7).
 *
 * <p><strong>It carries the way to stop it, and that is not decoration.</strong>
 * The preference it is subject to lives on an account nobody has visited yet,
 * so a person reading this has had no chance to turn it off — this link is
 * what makes the setting reachable at all, from the one place they are
 * standing when the question occurs to them.
 *
 * <p>The link is a page, not an action. Bolum 40.3 is about corporate
 * gateways that fetch every URL in a message before a person sees it; an
 * unsubscribe that acted on the fetch would switch off email for people who
 * never clicked. So it lands somewhere with a button, the same shape the magic
 * link uses for the same reason.
 *
 * <p>Absolute rule 4: it says nothing about the profile, because at this
 * moment there is not one.
 */
public final class WelcomeEmail {

    private WelcomeEmail() {
    }

    public static EmailMessage to(String recipient, String locale, String unsubscribeUrl) {
        return isTurkish(locale)
                ? turkish(recipient, unsubscribeUrl)
                : english(recipient, unsubscribeUrl);
    }

    /** Absolute rule 7, in the branch that decides which language to write. */
    private static boolean isTurkish(String locale) {
        return locale != null && locale.toLowerCase(Locale.ROOT).startsWith("tr");
    }

    private static EmailMessage turkish(String recipient, String unsubscribeUrl) {
        String subject = "AtomCV'ye hoş geldiniz";
        String text = """
                Merhaba,

                AtomCV hesabınız hazır. Buradaki fikir şu: özgeçmişinizi bir
                dosya olarak değil, yapılandırılmış bir veri kümesi olarak bir
                kez kuruyorsunuz; her ilan için ondan bir CV üretiliyor ve
                sayfa sınırı matematiksel olarak tutuluyor.

                Başlamak için mevcut CV'nizi yükleyebilir ya da profili
                sıfırdan yazabilirsiniz.

                Bu tür bilgilendirme e-postalarını istemiyorsanız buradan
                kapatabilirsiniz:

                %s
                """.formatted(unsubscribeUrl);
        String html = html(
                "AtomCV'ye hoş geldiniz",
                "Hesabınız hazır. Özgeçmişinizi bir kez yapılandırılmış olarak kuruyorsunuz; "
                        + "her ilan için ondan bir CV üretiliyor ve sayfa sınırı tutuyor.",
                "Bu tür bilgilendirme e-postalarını istemiyorsanız",
                "kapatın",
                unsubscribeUrl);
        return new EmailMessage(recipient, subject, text, html);
    }

    private static EmailMessage english(String recipient, String unsubscribeUrl) {
        String subject = "Welcome to AtomCV";
        String text = """
                Hello,

                Your AtomCV account is ready. The idea is this: you build your
                professional history once as a structured dataset rather than
                as a file, and a CV is generated from it for each job — with
                the page limit kept mathematically.

                To start, upload the CV you have or write the profile from
                scratch.

                If you would rather not receive emails like this one, you can
                turn them off here:

                %s
                """.formatted(unsubscribeUrl);
        String html = html(
                "Welcome to AtomCV",
                "Your account is ready. You build your history once as structured data, "
                        + "and a CV is generated from it for each job with the page limit kept.",
                "If you would rather not receive emails like this one,",
                "turn them off",
                unsubscribeUrl);
        return new EmailMessage(recipient, subject, text, html);
    }

    /**
     * {@link MagicLinkEmail}'s layout with the button spent on the footer
     * rather than on the message: the thing worth a button here is the way
     * out, since everything else is already in the reader's browser.
     */
    private static String html(
            String heading, String lead, String footer, String link, String url) {
        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;\
                max-width:520px;margin:0 auto;padding:32px 24px;color:#16202b;line-height:1.6">
                  <h1 style="font-size:20px;margin:0 0 12px">%s</h1>
                  <p style="margin:0 0 28px;color:#4e6072">%s</p>
                  <p style="margin:0;font-size:13px;color:#7a8b9b">%s
                    <a href="%s" style="color:#7a8b9b">%s</a>.
                  </p>
                </div>
                """.formatted(heading, lead, footer, url, link);
    }
}
