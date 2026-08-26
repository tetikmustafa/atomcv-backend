package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.identity.domain.AuthMethod;
import com.mustafatetik.atomcv.identity.domain.OAuthAccount;
import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.domain.UserAccount;
import com.mustafatetik.atomcv.identity.oauth.OAuthFailure;
import com.mustafatetik.atomcv.identity.repository.SignInAccounts;
import com.mustafatetik.atomcv.shared.security.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Find, then link, then create — and the order is the whole security argument. */
class OAuthLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");

    private final SignInAccounts accounts = mock(SignInAccounts.class);
    private final SessionStore sessions = mock(SessionStore.class);

    private OAuthLoginService service;

    @BeforeEach
    void setUp() {
        service = new OAuthLoginService(accounts, sessions, Clock.fixed(NOW, ZoneOffset.UTC));
        when(sessions.create(any(), any(), any())).thenAnswer(call -> Session.beginning(
                "a-session-id", call.getArgument(0), call.getArgument(1),
                call.getArgument(2), NOW));
    }

    /**
     * The provider's own subject decides, whatever the address says now. A
     * person who changed their email keeps their account — and a stranger who
     * later inherits the old address does not receive it.
     */
    @Test
    void aSubjectThatHasSignedInBeforeGoesStraightToItsAccount() {
        UserAccount existing = UserAccount.signingUp("old@example.com", "Ada");
        when(accounts.byProviderIdentity(OAuthProvider.GITHUB, "42"))
                .thenReturn(Optional.of(existing));

        var outcome = service.signIn(account(OAuthProvider.GITHUB, "42", "new@example.com", true));

        assertThat(outcome).isInstanceOf(SignInOutcome.SignedIn.class);
        verify(accounts, never()).byVerifiedEmail(any());
        verify(accounts, never()).create(any(), any());
        verify(sessions).create(existing.getId(), UserRole.USER, AuthMethod.OAUTH_GITHUB);
    }

    /**
     * Someone who signed up with Google and comes back through GitHub lands in
     * the same account, not a second one they cannot tell apart.
     */
    @Test
    void aVerifiedAddressAlreadyOnFileGainsTheNewIdentity() {
        UserAccount existing = UserAccount.signingUp("ada@example.com", null);
        when(accounts.byProviderIdentity(OAuthProvider.GOOGLE, "sub-1"))
                .thenReturn(Optional.empty());
        when(accounts.byVerifiedEmail("ada@example.com")).thenReturn(Optional.of(existing));

        var outcome = service.signIn(
                account(OAuthProvider.GOOGLE, "sub-1", "ada@example.com", true));

        assertThat(outcome).isInstanceOf(SignInOutcome.SignedIn.class);
        verify(accounts).link(existing.getId(), OAuthProvider.GOOGLE, "sub-1");
        verify(accounts, never()).create(any(), any());
        assertThat(existing.isEmailVerified()).isTrue();
        // A row that had no name gets the one the provider offered.
        assertThat(existing.getDisplayName()).isEqualTo("Ada Lovelace");
    }

    @Test
    void anUnknownSubjectWithAnUnknownAddressBecomesANewAccount() {
        when(accounts.byProviderIdentity(any(), any())).thenReturn(Optional.empty());
        when(accounts.byVerifiedEmail(any())).thenReturn(Optional.empty());
        UserAccount created = UserAccount.signingUp("new@example.com", "Ada Lovelace");
        when(accounts.create("new@example.com", "Ada Lovelace")).thenReturn(created);

        var outcome = service.signIn(
                account(OAuthProvider.GOOGLE, "sub-2", "new@example.com", true));

        assertThat(outcome).isInstanceOf(SignInOutcome.SignedIn.class);
        verify(accounts).link(created.getId(), OAuthProvider.GOOGLE, "sub-2");
        verify(accounts).seen(eq(created), eq(NOW));
    }

    /**
     * The defence that matters, and it is checked in a second place on
     * purpose: the takeover is a stranger adding someone else's address to
     * their own provider account. The adapters refuse first — a defence that
     * lives in exactly one place lives in the wrong place.
     */
    @Test
    void anUnverifiedAddressNeverReachesTheLookup() {
        var outcome = service.signIn(
                account(OAuthProvider.GITHUB, "99", "victim@example.com", false));

        assertThat(outcome).isEqualTo(new SignInOutcome.Refused(OAuthFailure.EMAIL_UNVERIFIED));
        verify(accounts, never()).byProviderIdentity(any(), any());
        verify(accounts, never()).byVerifiedEmail(any());
        verify(sessions, never()).create(any(), any(), any());
    }

    private static OAuthAccount account(
            OAuthProvider provider, String uid, String email, boolean verified) {
        return new OAuthAccount(provider, uid, email, verified, "Ada Lovelace");
    }
}
