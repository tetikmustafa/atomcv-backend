package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.identity.domain.AuthMethod;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import com.mustafatetik.atomcv.shared.security.UserRole;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Who a cookieless request is in local development (Sapma, Adim 3.3).
 *
 * <p>Sessions landed before any way to start one: OAuth is the next slice.
 * Without this, {@code make dev} would answer every call with
 * {@code AUTHENTICATION_REQUIRED} and the integration suite — twenty-seven
 * classes that drive the API through MockMvc with no cookie jar — would fail
 * on authentication rather than on what each of them is about.
 *
 * <p><strong>Switch it off to watch a real sign-in.</strong> With it on,
 * signing out drops straight back to the dev user and the browser looks signed
 * in again — confusing at exactly the moment you are trying to see a logout
 * work. {@code LOCAL_DEV_SESSION=false} turns it off; signing in through a
 * provider works either way, because a real cookie always wins.
 *
 * <p><strong>It applies only when the request carries no {@code sid}.</strong>
 * A real cookie always wins, so the moment a login exists it takes precedence
 * without this having to be removed first. And it is
 * {@code @Profile("local")}: production has no such bean, so a request with no
 * session there is exactly that.
 *
 * <p><strong>The session is not written to Redis.</strong> Storing one would
 * put a write in front of every cookieless request and grow the user's session
 * index without bound, for a session nobody can revoke and nobody needs to.
 * {@link SessionStore}'s own wiring is proved by a test that signs in through
 * it and calls an endpoint with the cookie it gets back.
 */
@Component
@Profile("local")
@ConditionalOnProperty(
        name = "atomcv.local-dev-session.enabled", havingValue = "true", matchIfMissing = true)
public class LocalDevSessions {

    /**
     * Fixed and obviously not random: it never reaches a browser as a
     * credential, and a value that looked like a real session id would be
     * mistaken for one in a log or a bug report.
     */
    static final String SESSION_ID = "local-dev-session";

    private final Clock clock;

    LocalDevSessions(Clock clock) {
        this.clock = clock;
    }

    Session session() {
        return Session.beginning(
                SESSION_ID,
                LocalDevUser.DEV_USER_ID,
                UserRole.USER,
                AuthMethod.LOCAL_DEV,
                clock.instant());
    }
}
