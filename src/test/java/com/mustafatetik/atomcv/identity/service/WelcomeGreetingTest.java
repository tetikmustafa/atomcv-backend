package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.email.EmailMessage;
import com.mustafatetik.atomcv.email.EmailProperties;
import com.mustafatetik.atomcv.email.EmailSender;
import com.mustafatetik.atomcv.email.EmailSuppressions;
import com.mustafatetik.atomcv.identity.domain.UserAccount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Bolum 57.7's trigger, which the first draft of that section got wrong.
 *
 * <p>The dangerous reading is "when the account is created". Bolum 40.4 writes
 * a {@code users} row the moment anybody types an address into the sign-in
 * box, so that trigger would have mailed every address anyone cared to enter.
 * The case below that matters most is therefore the one that asserts an
 * absence.
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

    /**
     * <strong>The case the first spec draft would have failed.</strong> An
     * account that exists because somebody typed the address has never signed
     * in — and this asks about signing in, not about existing, so the
     * distinction has to come from somewhere else than the row's presence.
     */
    @Test
    void anaccountThatHasSignedInBeforeIsNotGreetedAgain() {
        UserAccount returning = UserAccount.signingUp("ada@example.com", "Ada");
        returning.seenAt(Instant.parse("2026-09-01T10:00:00Z"));

        greeting.greetIfFirstSignIn(returning);

        assertThat(sent).isEmpty();
    }

    @Test
    void thefirstSignInIsGreetedOnce() {
        UserAccount arriving = UserAccount.signingUp("ada@example.com", "Ada");

        greeting.greetIfFirstSignIn(arriving);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).to()).isEqualTo("ada@example.com");
    }

    /**
     * The link is what makes the preference reachable at all (Bolum 57.7), so
     * an email that lost it would be one nobody could act on.
     */
    @Test
    void itcarriesTheWayToStopIt() {
        UserAccount arriving = UserAccount.signingUp("ada@example.com", "Ada");

        greeting.greetIfFirstSignIn(arriving);

        String expected = "https://app.test/unsubscribe?t=" + arriving.getUnsubscribeToken();
        assertThat(sent.get(0).text()).contains(expected);
        assertThat(sent.get(0).html()).contains(expected);
    }

    @Test
    void anaccountThatAlreadySaidNoIsNotGreeted() {
        UserAccount arriving = UserAccount.signingUp("ada@example.com", "Ada");
        arriving.setLifecycleEmails(false);

        greeting.greetIfFirstSignIn(arriving);

        assertThat(sent).isEmpty();
    }

    /** Bolum 57.7: a hard bounce outranks the preference, not the other way round. */
    @Test
    void asuppressedAddressIsNotWrittenToEvenWhenTheyWantIt() {
        when(suppressions.isSuppressed("ada@example.com")).thenReturn(true);
        UserAccount arriving = UserAccount.signingUp("ada@example.com", "Ada");

        greeting.greetIfFirstSignIn(arriving);

        assertThat(sent).isEmpty();
    }

    /** A sender that refuses does not refuse somebody their first sign-in. */
    @Test
    void asenderThatFailsIsNotAnexception() {
        var refusing = new WelcomeGreeting(message -> false, suppressions, PROPERTIES);

        refusing.greetIfFirstSignIn(UserAccount.signingUp("ada@example.com", "Ada"));
    }
}
