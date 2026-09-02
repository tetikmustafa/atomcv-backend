package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.billing.UsageCounters;
import com.mustafatetik.atomcv.identity.repository.SignInAccounts;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bolum 57.4's ordering, as the one thing a unit test can hold it to.
 *
 * <p>The cascade itself is checked against the schema in {@code
 * AccountDeletionIT}, which reads the tables out of {@code information_schema}
 * rather than naming them. What is left here is the half the database does not
 * do: the sessions go first, and the account does not go without them.
 */
class AccountDeletionServiceTest {

    private static final UUID SOMEONE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final SignInAccounts accounts = mock(SignInAccounts.class);

    private final SessionStore sessions = mock(SessionStore.class);

    private final UsageCounters counters = mock(UsageCounters.class);

    /**
     * <strong>The guard the ordering comment always claimed and never had.</strong>
     * {@code revokeAllFor} used to warn and answer zero when Redis could not
     * be reached, which is indistinguishable from a user who had no sessions
     * — so the deletion carried on and left live cookies pointing at an
     * account that was gone. Refusing keeps the two consistent, and a second
     * press costs nothing.
     */
    @Test
    void anAccountIsNotDeletedWhileItsSessionsCouldNotBe() {
        when(sessions.revokeAllFor(SOMEONE))
                .thenThrow(new SessionStoreUnavailableException(new IllegalStateException("down")));

        var service = new AccountDeletionService(accounts, sessions, counters);

        assertThatThrownBy(() -> service.delete(UserContext.of(SOMEONE)))
                .isInstanceOf(SessionStoreUnavailableException.class);
        verify(accounts, never()).deleteById(any());
        verify(counters, never()).forget(any());
    }
}
