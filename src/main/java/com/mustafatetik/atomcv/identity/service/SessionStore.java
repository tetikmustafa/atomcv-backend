package com.mustafatetik.atomcv.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.identity.domain.AuthMethod;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.shared.security.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Sessions in Redis, which is what Bolum 40.1 bought instead of a JWT.
 *
 * <p>The section's argument is revocation: a signed token cannot be taken
 * back, and a row that can be deleted can. That only holds if deleting is
 * actually reachable, so this keeps a second key per user — a set of their
 * live session ids — and {@link #revokeAllFor} is the operation Bolum 40.1's
 * "aninda" claim rests on. Building the index later would leave every session
 * written before it unrevokable.
 *
 * <p><strong>A Redis failure is a request without a session, never a request
 * that is let through.</strong> {@link com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysisCache}
 * treats an outage as a cache miss because it is an optimisation; this is the
 * authentication decision itself, and the only safe reading of "we cannot
 * tell" is "not signed in". {@link #create} does not swallow anything at all:
 * a session the store never kept is a login that appears to work and fails on
 * the next request.
 */
@Service
public class SessionStore {

    private static final String KEY_PREFIX = "sess:";

    private static final String USER_INDEX_PREFIX = "sess:user:";

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final SessionProperties properties;
    private final Clock clock;

    SessionStore(StringRedisTemplate redis, ObjectMapper json,
            SessionProperties properties, Clock clock) {
        this.redis = redis;
        this.json = json;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Mints a session and writes it before the caller can set a cookie for it.
     *
     * @throws SessionStoreUnavailableException when the write did not land —
     *                                          the caller must not send a
     *                                          {@code sid} for a session that
     *                                          does not exist
     */
    public Session create(UUID userId, UserRole role, AuthMethod method) {
        Session session = Session.beginning(
                SessionIds.next(), userId, role, method, clock.instant());
        try {
            redis.opsForValue().set(
                    keyOf(session.id()), json.writeValueAsString(session), properties.ttl());
            redis.opsForSet().add(userIndexOf(userId), session.id());
            // The index outlives no session it points at; without this it is a
            // key that only ever grows.
            redis.expire(userIndexOf(userId), properties.ttl());
            return session;
        } catch (Exception unavailable) {
            throw new SessionStoreUnavailableException(unavailable);
        }
    }

    /**
     * The session behind a cookie value, with its sliding TTL refreshed
     * (EK D.6.6).
     *
     * <p>The refresh is skipped when the session was seen within
     * {@link SessionProperties#touchInterval()}, so an active browser costs one
     * read per request rather than a read and a write.
     */
    public Optional<Session> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        try {
            String stored = redis.opsForValue().get(keyOf(sessionId));
            if (stored == null) {
                return Optional.empty();
            }
            Session session = json.readValue(stored, Session.class);
            return Optional.of(touch(session));
        } catch (Exception unavailableOrStale) {
            // Includes a stored value that no longer fits the record. Both mean
            // the same thing here: we cannot say who this is, so nobody is.
            log.warn("Session lookup failed, treating the request as unauthenticated: {}",
                    unavailableOrStale.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Sign-out: the cookie is cleared too, but this is what ends the session. */
    public void revoke(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            find(sessionId).ifPresent(
                    session -> redis.opsForSet().remove(userIndexOf(session.userId()), sessionId));
            redis.delete(keyOf(sessionId));
        } catch (Exception unavailable) {
            log.warn("Session revocation failed: {}", unavailable.getClass().getSimpleName());
        }
    }

    /**
     * Every browser this user is signed in on. What Bolum 40.1 promises over a
     * JWT, and the operation a password reset, a role change or a stolen
     * device needs.
     *
     * @return how many sessions were removed
     */
    public int revokeAllFor(UUID userId) {
        try {
            Set<String> ids = redis.opsForSet().members(userIndexOf(userId));
            if (ids == null || ids.isEmpty()) {
                return 0;
            }
            redis.delete(ids.stream().map(SessionStore::keyOf).toList());
            redis.delete(userIndexOf(userId));
            return ids.size();
        } catch (Exception unavailable) {
            log.warn("Bulk session revocation failed: {}", unavailable.getClass().getSimpleName());
            return 0;
        }
    }

    private Session touch(Session session) throws Exception {
        Instant now = clock.instant();
        if (Duration.between(session.lastSeenAt(), now).compareTo(properties.touchInterval()) < 0) {
            return session;
        }
        Session refreshed = session.seenAt(now);
        redis.opsForValue().set(
                keyOf(refreshed.id()), json.writeValueAsString(refreshed), properties.ttl());
        redis.expire(userIndexOf(refreshed.userId()), properties.ttl());
        return refreshed;
    }

    private static String keyOf(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    private static String userIndexOf(UUID userId) {
        return USER_INDEX_PREFIX + userId;
    }
}
