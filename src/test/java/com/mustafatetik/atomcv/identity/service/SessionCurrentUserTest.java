package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.identity.domain.AuthMethod;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.domain.UserAccount;
import com.mustafatetik.atomcv.identity.repository.SignInAccounts;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import com.mustafatetik.atomcv.shared.security.UserRole;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Who a request is, and what happens when it is nobody. */
class SessionCurrentUserTest {

    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");

    private static final UUID SOMEONE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final SessionStore store = mock(SessionStore.class);

    private final SignInAccounts accounts = mock(SignInAccounts.class);

    private final SessionCookies cookies =
            new SessionCookies(new SessionProperties(null, null, null, null, null, true));

    @BeforeEach
    void theAccountExistsUnlessASaysOtherwise() {
        when(accounts.byId(any())).thenReturn(
                Optional.of(UserAccount.signingUp("someone@example.com", "Someone")));
    }

    @AfterEach
    void clearTheRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void aCookieThatResolvesIsTheActingUser() {
        Session session = Session.beginning(
                "a-session-id", SOMEONE, UserRole.USER, AuthMethod.OAUTH_GOOGLE, NOW);
        when(store.find("a-session-id")).thenReturn(Optional.of(session));
        bindRequestCarrying("a-session-id");

        var currentUser = new SessionCurrentUser(store, cookies, accounts, noLocalDevBean());

        assertThat(currentUser.find()).contains(session.asUserContext());
        assertThat(currentUser.session()).contains(session);
    }

    /**
     * The whole reason {@code AUTHENTICATION_REQUIRED} was added to the
     * catalogue (Adim 3.3, Ekleme): a caller with no session gets a 401 that
     * says so, and a resolution that offers the only way forward.
     */
    @Test
    void withoutASessionRequireEndsTheRequestWithTheCatalogueCode() {
        bindRequestWithoutCookies();

        var currentUser = new SessionCurrentUser(store, cookies, accounts, noLocalDevBean());

        assertThat(currentUser.find()).isEmpty();
        assertThatThrownBy(currentUser::require)
                .isInstanceOfSatisfying(ApiException.class, thrown -> {
                    assertThat(thrown.error().code()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
                    assertThat(thrown.error().httpStatus()).isEqualTo(401);
                    assertThat(thrown.error().resolutions())
                            .singleElement()
                            .satisfies(resolution -> assertThat(resolution.action())
                                    .isEqualTo(ResolutionAction.SIGN_UP));
                });
    }

    /**
     * A revoked or expired cookie must not fall through to the local stand-in.
     * If it did, signing out in development would silently sign you back in as
     * the dev user, and the session that Bolum 40.1 promises can be revoked
     * would look revoked while behaving otherwise.
     */
    @Test
    void aCookieThatNoLongerResolvesIsNobodyEvenWhereTheLocalStandInExists() {
        when(store.find("a-revoked-id")).thenReturn(Optional.empty());
        bindRequestCarrying("a-revoked-id");

        var currentUser = new SessionCurrentUser(store, cookies, accounts, localDevBeanAvailable());

        assertThat(currentUser.find()).isEmpty();
    }

    @Test
    void withoutACookieTheLocalStandInAnswersAndNeverTouchesTheStore() {
        bindRequestWithoutCookies();

        var currentUser = new SessionCurrentUser(store, cookies, accounts, localDevBeanAvailable());

        assertThat(currentUser.find())
                .map(user -> user.userId())
                .contains(LocalDevUser.DEV_USER_ID);
        verify(store, never()).find(any());
    }

    /**
     * A controller and the service below it both ask, and the sliding TTL of
     * EK D.6.6 refreshes once per request rather than once per caller.
     */
    @Test
    void theAnswerIsResolvedOncePerRequest() {
        when(store.find("a-session-id")).thenReturn(Optional.of(Session.beginning(
                "a-session-id", SOMEONE, UserRole.USER, AuthMethod.MAGIC_LINK, NOW)));
        bindRequestCarrying("a-session-id");

        var currentUser = new SessionCurrentUser(store, cookies, accounts, noLocalDevBean());
        currentUser.find();
        currentUser.require();
        currentUser.session();

        verify(store, times(1)).find("a-session-id");
        verify(accounts, times(1)).byId(SOMEONE);
    }

    /**
     * <strong>F-027.</strong> A session can outlive the account it points at
     * — a request already in flight when the row went, or a revocation Redis
     * could not carry out. What that used to get was a 500, and only from the
     * endpoints that write: reading the profile creates its row on first use
     * and the insert broke a foreign key, while the generation list answered
     * 200 as though the account were fine.
     */
    @Test
    void aSessionThatOutlivedItsAccountIsNobodyAndIsRevoked() {
        when(store.find("a-session-id")).thenReturn(Optional.of(Session.beginning(
                "a-session-id", SOMEONE, UserRole.USER, AuthMethod.MAGIC_LINK, NOW)));
        when(accounts.byId(SOMEONE)).thenReturn(Optional.empty());
        bindRequestCarrying("a-session-id");

        var currentUser = new SessionCurrentUser(store, cookies, accounts, noLocalDevBean());

        assertThat(currentUser.find()).isEmpty();
        assertThatThrownBy(currentUser::require)
                .isInstanceOfSatisfying(ApiException.class, thrown -> {
                    assertThat(thrown.error().code()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
                    assertThat(thrown.error().httpStatus()).isEqualTo(401);
                });
        // Bolum 40.1: a stored session pointing at a deleted account is the
        // state the section says must not exist, so seeing one ends it.
        verify(store).revoke("a-session-id");
    }

    /**
     * The same check on the cookieless path, which is where the frontend's
     * measurement hit it: {@code make dev} answers as the dev user, and after
     * {@code DELETE /account} that user is a row that is not there.
     */
    @Test
    void theLocalStandInIsCheckedAgainstTheAccountToo() {
        when(accounts.byId(LocalDevUser.DEV_USER_ID)).thenReturn(Optional.empty());
        bindRequestWithoutCookies();

        var currentUser = new SessionCurrentUser(store, cookies, accounts, localDevBeanAvailable());

        assertThat(currentUser.find()).isEmpty();
        assertThat(currentUser.session()).isEmpty();
    }

    /**
     * An anonymous session has no account, so there is nothing to check and no
     * lookup to spend on it (Adim 3.6).
     */
    @Test
    void anAnonymousSessionIsNeverLookedUpAsAnAccount() {
        when(store.find("an-anonymous-id"))
                .thenReturn(Optional.of(Session.anonymous("an-anonymous-id", NOW)));
        bindRequestCarrying("an-anonymous-id");

        var currentUser = new SessionCurrentUser(store, cookies, accounts, noLocalDevBean());

        assertThat(currentUser.anonymousSession()).isPresent();
        assertThat(currentUser.find()).isEmpty();
        verify(accounts, never()).byId(any());
    }

    /**
     * The queue worker, the anomaly detector, a scheduled task. None of them
     * act as a user; each carries its subject explicitly.
     */
    @Test
    void outsideARequestThereIsNobodyRatherThanAFailure() {
        var currentUser = new SessionCurrentUser(store, cookies, accounts, localDevBeanAvailable());

        assertThat(currentUser.session()).isEmpty();
        assertThat(currentUser.find()).isEmpty();
    }

    private void bindRequestCarrying(String sessionId) {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("sid", sessionId));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void bindRequestWithoutCookies() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<LocalDevSessions> noLocalDevBean() {
        ObjectProvider<LocalDevSessions> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<LocalDevSessions> localDevBeanAvailable() {
        ObjectProvider<LocalDevSessions> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new LocalDevSessions(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        return provider;
    }

}
