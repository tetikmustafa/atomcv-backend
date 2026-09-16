package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.email.EmailMessage;
import com.mustafatetik.atomcv.email.EmailProperties;
import com.mustafatetik.atomcv.email.EmailSender;
import com.mustafatetik.atomcv.email.EmailSuppressions;
import com.mustafatetik.atomcv.identity.domain.UserAccount;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The trigger, which the first draft of that section got wrong.
 *
 * <p>The dangerous reading is "when the account is created". A {@code users}
 * row is written the moment anybody types an address into the sign-in box, so
 * that trigger would have mailed every address anyone cared to enter.
 *
 * <p><strong>That case is no longer here.</strong> "Has this person signed in
 * before" is answerable only before {@code seen(...)} runs, which is inside
 * the transaction this class now deliberately sits outside of, so the two
 * sign-in routes ask it and publish {@link FirstSignIn} only when the answer
 * is yes. The assertion moved with the decision: see
 * {@code MagicLinkServiceTest} and {@code OAuthLoginServiceTest}. What is left
 * here is the other half — whether an address may be written to at all.
 */
class WelcomeGreetingTest {

    private static final EmailProperties PROPERTIES = new EmailProperties(
            "no-reply@mail.example.com", "AtomCV", null, null, "https://app.test/unsubscribe");

    private List<EmailMessage> sent;
    private EmailSuppressions suppressions;
    private WelcomeGreeting greeting;

    @BeforeEach
    void setUp() {
        sent = new ArrayList<>();
        EmailSender sender = message -> {
            sent.add(message);
            return true;
        };
        suppressions = mock(EmailSuppressions.class);
        greeting = new WelcomeGreeting(sender, suppressions, PROPERTIES);
    }

    @Test
    void thefirstSignInIsGreetedOnce() {
        UserAccount arriving = UserAccount.signingUp("ada@example.com", "Ada");

        greeting.greet(arriving);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).to()).isEqualTo("ada@example.com");
    }

    /**
     * The link is what makes the preference reachable at all, so an email that
     * lost it would be one nobody could act on.
     */
    @Test
    void itcarriesTheWayToStopIt() {
        UserAccount arriving = UserAccount.signingUp("ada@example.com", "Ada");

        greeting.greet(arriving);

        String expected = "https://app.test/unsubscribe?t=" + arriving.getUnsubscribeToken();
        assertThat(sent.get(0).text()).contains(expected);
        assertThat(sent.get(0).html()).contains(expected);
    }

    @Test
    void anaccountThatAlreadySaidNoIsNotGreeted() {
        UserAccount arriving = UserAccount.signingUp("ada@example.com", "Ada");
        arriving.setLifecycleEmails(false);

        greeting.greet(arriving);

        assertThat(sent).isEmpty();
    }

    /** A hard bounce outranks the preference, not the other way round. */
    @Test
    void asuppressedAddressIsNotWrittenToEvenWhenTheyWantIt() {
        when(suppressions.isSuppressed("ada@example.com")).thenReturn(true);
        UserAccount arriving = UserAccount.signingUp("ada@example.com", "Ada");

        greeting.greet(arriving);

        assertThat(sent).isEmpty();
    }

    /** A sender that refuses does not refuse somebody their first sign-in. */
    @Test
    void asenderThatFailsIsNotAnexception() {
        var refusing = new WelcomeGreeting(message -> false, suppressions, PROPERTIES);

        refusing.greet(UserAccount.signingUp("ada@example.com", "Ada"));
    }
}
