package com.mustafatetik.atomcv.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The last thing this product says to somebody (Bolum 57.4, 57.7).
 *
 * <p>Two of these assert an absence, which is the whole reason the class is
 * separate from {@link MagicLinkEmail}: this message has nowhere to send
 * anyone, and it carries nothing that was in the account it is about.
 */
class AccountDeletedEmailTest {

    @Test
    void itsaysWhatWentInTheReadersOwnLanguage() {
        var turkish = AccountDeletedEmail.to("ada@example.com", "tr");
        var english = AccountDeletedEmail.to("ada@example.com", "en");

        assertThat(turkish.subject()).isEqualTo("AtomCV hesabınız silindi");
        assertThat(turkish.text()).contains("profiliniz").contains("geri alınamaz");
        assertThat(english.subject()).isEqualTo("Your AtomCV account has been deleted");
        assertThat(english.text()).contains("your profile").contains("cannot be undone");
    }

    /** Bolum 40.2's rule, and this message is no exception to it. */
    @Test
    void bothPartsTravel() {
        var message = AccountDeletedEmail.to("ada@example.com", "en");

        assertThat(message.text()).isNotBlank();
        assertThat(message.html()).contains("<h1").contains("has been deleted");
    }

    /**
     * <strong>No link anywhere.</strong> Every other message ends in somewhere
     * to go; this one cannot, because the account a link would lead to is what
     * was just deleted. A copy-paste from the magic link's layout would bring
     * a button with it, and the button would be broken for everyone who got it.
     */
    @Test
    void thereIsNowhereToSendThem() {
        for (String locale : new String[] {"tr", "en"}) {
            var message = AccountDeletedEmail.to("ada@example.com", locale);

            assertThat(message.html()).doesNotContain("<a ").doesNotContain("href");
            assertThat(message.text()).doesNotContain("http");
        }
    }

    /** Absolute rule 4: nothing that was in the account reaches the message. */
    @Test
    void ittellsThemNothingAboutWhatTheyHad() {
        var message = AccountDeletedEmail.to("ada@example.com", "en");

        assertThat(message.text() + message.html())
                .as("the address is the recipient, not the content")
                .doesNotContain("ada@example.com");
    }

    /** Absolute rule 7, in the branch that decides which language to write. */
    @Test
    void anullLocaleIsEnglishRatherThanAnexception() {
        assertThat(AccountDeletedEmail.to("ada@example.com", null).subject())
                .isEqualTo("Your AtomCV account has been deleted");
        assertThat(AccountDeletedEmail.to("ada@example.com", "TR").subject())
                .as("the Turkish locale is matched whatever case it arrives in")
                .isEqualTo("AtomCV hesabınız silindi");
    }
}
