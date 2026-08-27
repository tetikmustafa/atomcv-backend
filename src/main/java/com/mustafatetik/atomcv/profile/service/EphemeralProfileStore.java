package com.mustafatetik.atomcv.profile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Where an anonymous person's profile lives (Bolum 9, Adim 3.6).
 *
 * <p><strong>Redis, and nowhere else.</strong> Bolum 9 promises that somebody
 * who has not signed up leaves nothing behind, and the build guide makes that
 * a test rather than a sentence: an anonymous session must write no row to any
 * table. A profile in Postgres with a nullable owner and a cleanup job would
 * keep the promise on paper and break it in a backup.
 *
 * <p><strong>The window slides, and it slides with the session.</strong> An
 * absolute two hours would cut somebody off in the middle of the review screen
 * — the lost effort design principle 8 exists to prevent. The TTL is the
 * session's own, so a person still working still has a profile, and the two
 * cannot expire apart.
 *
 * <p>Refusing rather than falling back when Redis is unreachable: an anonymous
 * profile has no second home, so an empty answer would read as "you have not
 * uploaded anything" to somebody who just did.
 */
@Service
public class EphemeralProfileStore {

    private static final String PREFIX = "anon:profile:";

    private static final Logger log = LoggerFactory.getLogger(EphemeralProfileStore.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    EphemeralProfileStore(StringRedisTemplate redis, ObjectMapper json,
            @Value("${atomcv.session.anonymous-ttl:2h}") Duration ttl) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    /**
     * @throws IllegalArgumentException if the scope is not ephemeral — the
     *                                  type exists so that a persistent
     *                                  profile arriving here is a compile-time
     *                                  smell and a run-time refusal, never a
     *                                  quiet write to the wrong store
     */
    public void save(ProfileRef profile, EphemeralProfile stored) {
        requireEphemeral(profile);
        try {
            redis.opsForValue().set(keyOf(profile), json.writeValueAsString(stored), ttl);
            // Counts, never a line of the CV (absolute rule 4).
            log.info("Stored an anonymous profile: {}", stored.shape());
        } catch (Exception unavailable) {
            throw new EphemeralProfileUnavailableException(unavailable);
        }
    }

    /**
     * Reads it back and slides the window, because reading is activity
     * (EK D.6.6).
     *
     * @return empty when the window has closed, which is the one case a caller
     *         answers with {@code ANONYMOUS_SESSION_EXPIRED}
     */
    public Optional<EphemeralProfile> find(ProfileRef profile) {
        requireEphemeral(profile);
        try {
            String stored = redis.opsForValue().get(keyOf(profile));
            if (stored == null) {
                return Optional.empty();
            }
            redis.expire(keyOf(profile), ttl);
            return Optional.of(json.readValue(stored, EphemeralProfile.class));
        } catch (Exception unavailableOrStale) {
            // A stored value that no longer fits the record lands here too,
            // and it means the same thing to the caller: this cannot be read.
            throw new EphemeralProfileUnavailableException(unavailableOrStale);
        }
    }

    /** What the upgrade to an account does once the rows are written. */
    public void discard(ProfileRef profile) {
        requireEphemeral(profile);
        try {
            redis.delete(keyOf(profile));
        } catch (Exception unavailable) {
            // Nothing is lost by failing here: the TTL removes it anyway, and
            // an exception would fail an upgrade that has already succeeded.
            log.warn("Could not discard an anonymous profile: {}",
                    unavailable.getClass().getSimpleName());
        }
    }

    private static void requireEphemeral(ProfileRef profile) {
        if (profile.scope() != ProfileRef.Scope.EPHEMERAL) {
            throw new IllegalArgumentException(
                    "A persistent profile is not stored here (§ 41.3)");
        }
    }

    private static String keyOf(ProfileRef profile) {
        return PREFIX + profile.id();
    }
}
