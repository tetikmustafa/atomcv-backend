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
 * @param userId     the row in {@code users}
 * @param role       as of the moment the session was created
 * @param method     how they signed in
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
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Session id must not be blank");
        }
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
