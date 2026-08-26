package com.mustafatetik.atomcv.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The one email the product sends, in the two languages it sends it in. */
class MagicLinkEmailTest {

    private static final String URL = "https://atomcv.example.com/verify?s=abc&v=def";

    @Test
    void aTurkishAccountGetsTurkish() {
        var message = MagicLinkEmail.to("ada@example.com", "tr", URL, 10);

        assertThat(message.subject()).isEqualTo("AtomCV giriş bağlantınız");
        assertThat(message.text()).contains("giriş yapmak için");
    }

    @Test
    void anythingElseGetsEnglish() {
        assertThat(MagicLinkEmail.to("ada@example.com", "en", URL, 10).subject())
                .isEqualTo("Your AtomCV sign-in link");
        assertThat(MagicLinkEmail.to("ada@example.com", null, URL, 10).subject())
                .isEqualTo("Your AtomCV sign-in link");
    }

    /**
     * The encoding pin in {@code build.gradle.kts}, watched holding.
     *
     * <p>{@code native.encoding} is {@code Cp1254} on the machine this is
     * written on and UTF-8 on the runner. If the pin ever came out, these
     * characters would arrive in somebody's inbox as mojibake — and it would
     * pass on CI, which is the half that makes it worth asserting rather than
     * trusting.
     */
    @Test
    void theTurkishCharactersSurviveCompilation() {
        var message = MagicLinkEmail.to("ada@example.com", "tr", URL, 10);

        assertThat(message.subject()).contains("ş").contains("ğ").contains("ı");
        assertThat(message.html()).contains("Aşağıdaki düğme");
        // The replacement character is what a decoding failure leaves behind.
        // Not a bare "?": the URL is full of them.
        assertThat(message.text()).doesNotContain("�");
        assertThat(message.subject()).doesNotContain("?");
    }

    /** Absolute rule 7: the branch itself must not depend on the default locale. */
    @Test
    void theLanguageIsChosenCaseInsensitivelyAndWithoutALocale() {
        assertThat(MagicLinkEmail.to("a@b.c", "TR", URL, 10).subject())
                .isEqualTo("AtomCV giriş bağlantınız");
        assertThat(MagicLinkEmail.to("a@b.c", "tr-TR", URL, 10).subject())
                .isEqualTo("AtomCV giriş bağlantınız");
    }

    /**
     * Both parts carry the link. A text-only message is filtered more often,
     * and the clients that refuse HTML are the same corporate gateways
     * Bolum 40.3 warns about.
     */
    @Test
    void theLinkIsInBothPartsAndTheLifetimeIsStated() {
        var message = MagicLinkEmail.to("ada@example.com", "en", URL, 10);

        assertThat(message.text()).contains(URL).contains("10 minutes");
        assertThat(message.html()).contains(URL).contains("10 minutes");
        // Also as text in the HTML part, for a client that will not render the
        // button or a person who wants to see where it goes.
        assertThat(message.html()).containsPattern("(?s)href=\"" + java.util.regex.Pattern.quote(URL));
    }

    @Test
    void theRecipientIsTheAddressItWasBuiltFor() {
        assertThat(MagicLinkEmail.to("ada@example.com", "en", URL, 10).to())
                .isEqualTo("ada@example.com");
    }
}
