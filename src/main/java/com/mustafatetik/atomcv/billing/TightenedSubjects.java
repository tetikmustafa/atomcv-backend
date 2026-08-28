package com.mustafatetik.atomcv.billing;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Who is on a shorter leash, and until when (Bolum 44.3).
 *
 * <p>Bolum 44.3's snippet ends its heavy-user branch with
 * {@code rateLimiter.tighten(u.userId(), Duration.ofHours(6))} and the section
 * then records that no such limiter exists — the detector reported and did
 * nothing. This is the missing half. The detector still does not pull the
 * brake, which stops everybody and stays a person's decision; it narrows the
 * one subject the numbers were about.
 *
 * <p><strong>Redis and not a column.</strong> The mark is a six-hour fact and
 * expiry is the whole of its lifecycle — a table would need a sweeper to say
 * the same thing, and a stale row there would keep somebody throttled after
 * the reason had passed. The TTL is the record.
 *
 * <p><strong>Unreachable means untightened.</strong> A cache outage must not
 * decide that everybody is a heavy user; the failure mode of this class is to
 * let work through, exactly as {@code JobAnalysisCache} treats a failure as a
 * miss.
 */
@Component
public class TightenedSubjects {

    private static final Logger log = LoggerFactory.getLogger(TightenedSubjects.class);
    private static final String PREFIX = "tightened:";

    private final StringRedisTemplate redis;

    TightenedSubjects(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Idempotent: a second alarm about the same subject extends the window. */
    public void tighten(String subjectId, Duration window) {
        try {
            redis.opsForValue().set(PREFIX + subjectId, "1", window);
            log.warn("Tightened {} for {}", subjectId, window);
        } catch (RuntimeException unreachable) {
            log.warn("Could not tighten {}: {}", subjectId, unreachable.toString());
        }
    }

    public boolean isTightened(String subjectId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + subjectId));
        } catch (RuntimeException unreachable) {
            log.warn("Could not read the tightening of {}, treating it as none: {}",
                    subjectId, unreachable.toString());
            return false;
        }
    }

    /** For an operator who has read the alarm and decided it was nothing. */
    public void release(String subjectId) {
        try {
            redis.delete(PREFIX + subjectId);
        } catch (RuntimeException unreachable) {
            log.warn("Could not release {}: {}", subjectId, unreachable.toString());
        }
    }
}
