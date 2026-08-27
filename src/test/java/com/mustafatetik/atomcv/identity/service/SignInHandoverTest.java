package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.identity.domain.AuthMethod;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.profile.service.ProfileUpgrade;
import com.mustafatetik.atomcv.profile.service.ProfileUpgradeService;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.security.UserRole;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The one line between signing in and the cookie being replaced (Adim 3.6).
 *
 * <p>Two things are worth holding here and neither is the upgrade itself,
 * which has its own tests against a real database: that the account the
 * profile is moved to is the one just signed in, and that a sign-in carrying
 * nothing does not go looking.
 */
class SignInHandoverTest {

    private static final UUID USER = UUID.randomUUID();

    private final CurrentUser caller = mock(CurrentUser.class);

    private final ProfileUpgradeService upgrades = mock(ProfileUpgradeService.class);

    private final SignInHandover handover = new SignInHandover(caller, upgrades);

    /**
     * The account is taken from the session just created, never from
     * {@code CurrentUser} — which is still answering as the anonymous session
     * the request arrived on, and would upgrade a profile into nobody.
     */
    @Test
    void theprofileMovesToTheAccountThatJustSignedIn() {
        var session = AnonymousSessionId.of("the-session-they-arrived-on");
        when(caller.anonymousSession()).thenReturn(Optional.of(session));
        when(upgrades.upgrade(any(), any())).thenReturn(ProfileUpgrade.UPGRADED);

        assertThat(handover.follow(signedIn())).isEqualTo(ProfileUpgrade.UPGRADED);

        verify(upgrades).upgrade(UserContext.of(USER), session);
    }

    /** Most sign-ins: nobody was carrying anything. */
    @Test
    void asignInWithNoAnonymousSessionAsksNothing() {
        when(caller.anonymousSession()).thenReturn(Optional.empty());

        assertThat(handover.follow(signedIn())).isEqualTo(ProfileUpgrade.NONE);

        verify(upgrades, never()).upgrade(any(), any());
    }

    private static Session signedIn() {
        return Session.beginning("a-new-session", USER, UserRole.USER,
                AuthMethod.MAGIC_LINK, Instant.EPOCH);
    }
}
