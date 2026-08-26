package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.email.EmailMessage;
import com.mustafatetik.atomcv.email.EmailSender;
import com.mustafatetik.atomcv.email.EmailSuppressions;
import com.mustafatetik.atomcv.identity.domain.AuthMethod;
import com.mustafatetik.atomcv.identity.domain.MagicLinkToken;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.domain.UserAccount;
import com.mustafatetik.atomcv.identity.repository.MagicLinkTokens;
import com.mustafatetik.atomcv.identity.repository.SignInAccounts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Bolum 40.2 and 40.4: what is issued, and what the answer never says. */
class MagicLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");

    private final SignInAccounts accounts = mock(SignInAccounts.class);
    private final MagicLinkTokens tokens = mock(MagicLinkTokens.class);
    private final SessionStore sessions = mock(SessionStore.class);
    private final EmailSender email = mock(EmailSender.class);
    private final EmailSuppressions suppressions = mock(EmailSuppressions.class);

    private MagicLinkService service;

    @BeforeEach
    void setUp() {
        service = new MagicLinkService(accounts, tokens, sessions, email, suppressions,
                new MagicLinkProperties("https://app.test", "/verify"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(email.send(any())).thenReturn(true);
        when(suppressions.isSuppressed(anyString())).thenReturn(false);
        when(tokens.save(any())).thenAnswer(call -> call.getArgument(0));
        when(sessions.create(any(), any(), any())).thenAnswer(call -> Session.beginning(
                "a-session-id", call.getArgument(0), call.getArgument(1),
                call.getArgument(2), NOW));
    }

    // ── requesting ────────────────────────────────────────────────────────

    /**
     * Bolum 40.4 in one assertion: an address with an account and one without
     * do the same work, so there is nothing for a caller to measure or read.
     */
    @Test
    void anUnknownAddressGetsAnAccountAndALinkJustLikeAKnownOne() {
        UserAccount created = UserAccount.awaitingVerification("new@example.com");
        when(accounts.byEmail("new@example.com")).thenReturn(Optional.empty());
        when(accounts.createAwaitingVerification("new@example.com")).thenReturn(created);

        service.request("new@example.com");

        verify(accounts).createAwaitingVerification("new@example.com");
        verify(tokens).save(any(MagicLinkToken.class));
        verify(email).send(any(EmailMessage.class));
    }

    @Test
    void aKnownAddressReusesItsAccount() {
        UserAccount existing = UserAccount.signingUp("ada@example.com", "Ada");
        when(accounts.byEmail("ada@example.com")).thenReturn(Optional.of(existing));

        service.request("ada@example.com");

        verify(accounts, never()).createAwaitingVerification(anyString());
        verify(tokens).save(any(MagicLinkToken.class));
        verify(email).send(any(EmailMessage.class));
    }

    /** Absolute rule 7, and the reason one person must be one row. */
    @Test
    void theAddressIsTrimmedAndLowercasedBeforeAnythingLooksAtIt() {
        when(accounts.byEmail("ada@example.com")).thenReturn(Optional.empty());
        when(accounts.createAwaitingVerification("ada@example.com"))
                .thenReturn(UserAccount.awaitingVerification("ada@example.com"));

        service.request("  ADA@Example.COM  ");

        verify(accounts).byEmail("ada@example.com");
    }

    /**
     * A hard bounce is a standing instruction, and ignoring it costs the
     * sending domain's reputation — which breaks sign-in for everyone, not for
     * this address. The token is still written, so the work stays the same.
     */
    @Test
    void aSuppressedAddressIsNotWrittenToButStillWalksTheSamePath() {
        when(accounts.byEmail("bounced@example.com"))
                .thenReturn(Optional.of(UserAccount.signingUp("bounced@example.com", null)));
        when(suppressions.isSuppressed("bounced@example.com")).thenReturn(true);

        service.request("bounced@example.com");

        verify(tokens).save(any(MagicLinkToken.class));
        verify(email, never()).send(any());
    }

    @Test
    void theLinkPointsAtThePageAndCarriesBothHalves() {
        when(accounts.byEmail(anyString()))
                .thenReturn(Optional.of(UserAccount.signingUp("ada@example.com", null)));

        service.request("ada@example.com");

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(email).send(sent.capture());
        assertThat(sent.getValue().text())
                .contains("https://app.test/verify?s=")
                .contains("&v=");
    }

    /**
     * The verifier is a credential and lives only in the email; what is kept
     * is a hash of it. A stolen dump must not sign anyone in.
     */
    @Test
    void whatIsStoredIsNotWhatIsSent() {
        when(accounts.byEmail(anyString()))
                .thenReturn(Optional.of(UserAccount.signingUp("ada@example.com", null)));

        service.request("ada@example.com");

        ArgumentCaptor<MagicLinkToken> stored = ArgumentCaptor.forClass(MagicLinkToken.class);
        verify(tokens).save(stored.capture());
        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(email).send(sent.capture());

        String verifier = sent.getValue().text().split("&v=")[1].split("\\s")[0];
        assertThat(stored.getValue().getVerifierHash()).isNotEqualTo(verifier);
        assertThat(sent.getValue().text()).doesNotContain(stored.getValue().getVerifierHash());
    }

    /** A sender that refused must not change the answer (Bolum 40.4). */
    @Test
    void aSenderThatRefusesDoesNotFailTheRequest() {
        when(accounts.byEmail(anyString()))
                .thenReturn(Optional.of(UserAccount.signingUp("ada@example.com", null)));
        when(email.send(any())).thenReturn(false);

        service.request("ada@example.com");

        verify(tokens).save(any(MagicLinkToken.class));
    }

    // ── redeeming ─────────────────────────────────────────────────────────

    @Test
    void aSelectorNobodyIssuedIsRefused() {
        when(tokens.bySelector("nope")).thenReturn(Optional.empty());

        assertThat(service.verify("nope", "anything")).isEmpty();
        assertThat(service.verify(null, "anything")).isEmpty();
        assertThat(service.verify("nope", null)).isEmpty();
    }

    @Test
    void aWrongVerifierIsRefusedAndNothingIsSpent() {
        when(tokens.bySelector("a-selector")).thenReturn(Optional.of(
                MagicLinkToken.issued("a-selector", "some-other-hash",
                        java.util.UUID.randomUUID(), NOW.plusSeconds(600))));

        assertThat(service.verify("a-selector", "a-guess")).isEmpty();
        verify(tokens, never()).redeem(any(), any());
    }

    @Test
    void anExpiredLinkIsRefused() {
        String verifier = issueAndCaptureVerifier(NOW.minusSeconds(1));

        assertThat(service.verify("a-selector", verifier)).isEmpty();
        verify(tokens, never()).redeem(any(), any());
    }

    /**
     * The race, settled by the database. Two requests arriving together both
     * pass every check above; only the one whose conditional update touched a
     * row gets a session.
     */
    @Test
    void losingTheRaceToRedeemIsRefused() {
        String verifier = issueAndCaptureVerifier(NOW.plusSeconds(600));
        when(tokens.redeem(any(), any())).thenReturn(false);

        assertThat(service.verify("a-selector", verifier)).isEmpty();
        verify(sessions, never()).create(any(), any(), any());
    }

    @Test
    void redeemingSignsInVerifiesTheAddressAndSpendsEverythingElse() {
        String verifier = issueAndCaptureVerifier(NOW.plusSeconds(600));
        UserAccount user = UserAccount.awaitingVerification("ada@example.com");
        when(tokens.redeem(any(), eq(NOW))).thenReturn(true);
        when(accounts.byId(any())).thenReturn(Optional.of(user));

        var session = service.verify("a-selector", verifier);

        assertThat(session).isPresent();
        assertThat(session.get().method()).isEqualTo(AuthMethod.MAGIC_LINK);
        // Opening the email is the proof, and this is where it lands.
        assertThat(user.isEmailVerified()).isTrue();
        verify(accounts).seen(eq(user), eq(NOW));
        verify(tokens).spendOutstandingFor(eq(user.getId()), eq(NOW));
    }

    /**
     * Issues a real link through the service so the verifier under test is one
     * the hashing actually produced, rather than one this test invented.
     */
    private String issueAndCaptureVerifier(Instant expiresAt) {
        when(accounts.byEmail(anyString()))
                .thenReturn(Optional.of(UserAccount.signingUp("ada@example.com", null)));
        service.request("ada@example.com");

        ArgumentCaptor<MagicLinkToken> stored = ArgumentCaptor.forClass(MagicLinkToken.class);
        verify(tokens).save(stored.capture());
        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(email).send(sent.capture());
        String verifier = sent.getValue().text().split("&v=")[1].split("\\s")[0];

        when(tokens.bySelector("a-selector")).thenReturn(Optional.of(
                MagicLinkToken.issued("a-selector", stored.getValue().getVerifierHash(),
                        java.util.UUID.randomUUID(), expiresAt)));
        return verifier;
    }
}
