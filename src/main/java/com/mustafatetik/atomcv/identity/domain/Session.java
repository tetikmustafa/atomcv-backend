package com.mustafatetik.atomcv.identity.domain;

import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.security.UserRole;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One signed-in browser, as the server remembers it (Bolum 40.1).
 *
 * <p>The record is what lives in Redis. It carries the role rather than
 * looking it up per request, which is the trade Bolum 40.1 already made when
 * it chose a server-side session over a JWT: a role change has to revoke the
 * session to take effect, and revocation here is a {@code DEL} rather than the
 * impossibility it is with a signed token.
 *
 * <p>It carries no email and no display name. Those are user content that a
 * log line or an error body could pick up by accident (absolute rule 4), and
 * the endpoint that needs them can read the row.
 *
 * @param id         the opaque value in the {@code sid} cookie — never a
 *                   {@code UUID}, so that nothing is tempted to treat it as a
 *                   database key
 * @param userId     the row in {@code users}, or null when nobody has signed
 *                   in — Adim 3.6's anonymous session, whose own id is the
 *                   identity everything below it is scoped by
 * @param role       as of the moment the session was created, or null when
 *                   anonymous
 * @param method     how they signed in, or null when they did not
 * @param createdAt  when the session began — fixed, and not moved by activity
 * @param lastSeenAt the last request that refreshed the sliding TTL (EK D.6.6)
 */
public record Session(
        String id,
        UUID userId,
        UserRole role,
        AuthMethod method,
        Instant createdAt,
        Instant lastSeenAt) {

    public Session {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Session id must not be blank");
        }
        // The three travel together or not at all. A session with a user and
        // no role would be one whose authorisation nobody decided, and a
        // session with a role and no user is a role belonging to nobody —
        // both are worse than either of the two states this actually has.
        boolean signedIn = userId != null;
        if (signedIn != (role != null) || signedIn != (method != null)) {
            throw new IllegalArgumentException(
                    "A session is signed in with all three of user, role and method, "
                            + "or anonymous with none of them");
        }
    }

    /**
     * A session for somebody who has not signed in (Adim 3.6, Bolum 9).
     *
     * <p>The same cookie as an account's, deliberately: § 35.7 makes
     * authentication a question the client asks {@code capabilities} rather
     * than a second credential to carry. What differs is the TTL — two hours
     * that slide, against thirty days.
     */
    public static Session anonymous(String id, Instant now) {
        return new Session(id, null, null, null, now, now);
    }

    /** Whether nobody has signed in on it. */
    public boolean isAnonymous() {
        return userId == null;
    }

    public static Session beginning(
            String id, UUID userId, UserRole role, AuthMethod method, Instant now) {
        return new Session(id, userId, role, method, now, now);
    }

    public Session seenAt(Instant now) {
        return new Session(id, userId, role, method, createdAt, now);
    }

    /** What every scoped read and write takes (Bolum 41.2). */
    public UserContext asUserContext() {
        return new UserContext(userId, role);
    }
}
