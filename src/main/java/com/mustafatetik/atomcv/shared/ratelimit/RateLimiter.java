package com.mustafatetik.atomcv.shared.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Bolum 40.5's counters, as a sliding window over Redis.
 *
 * <p><strong>A window and not a bucket.</strong> Bolum 02's table names
 * Bucket4j, but Bolum 40.5 states its limits as "3 requests / 15 minutes",
 * which is a window; a token bucket refilling at a fifth of a request per
 * minute is a different rule that happens to average the same. The deviation
 * is recorded in {@code notes/current.md}.
 *
 * <p><strong>A log and not a counter.</strong> Each admitted request is a
 * member of a sorted set scored by its arrival, so the window really slides:
 * a fixed counter would let twice the limit through across a boundary, and —
 * the reason that decided it — could not say when the next slot frees up.
 * {@code Retry-After} is only worth sending if that instant is real. The sets
 * hold at most {@code limit} members, ten of them at the widest layer, so the
 * cost of exactness here is nothing.
 *
 * <p><strong>One script, so the read and the write cannot be interleaved.</strong>
 * Trimming, counting and admitting from Java would let two requests arriving
 * together both see the last free slot — the same race {@code QuotaService}
 * avoids by counting inside its increment.
 */
@Component
public class RateLimiter {

    /**
     * Trim, count, admit — or report the moment the window opens again.
     *
     * <p>Returns {@code 0} when admitted, and otherwise the epoch-milli the
     * oldest member expires at. One integer rather than a table: Redis maps a
     * Lua table to a multi-bulk reply whose element types have to be
     * deserialised one by one, and there is nothing here a second value would
     * carry.
     *
     * <p>The member is scored by arrival but named by a random suffix, because
     * {@code ZADD} is an upsert: two requests in the same millisecond named by
     * that millisecond alone would be one member, and the second would be
     * admitted for free.
     */
    private static final RedisScript<Long> SLIDING_WINDOW = new DefaultRedisScript<>("""
            local key    = KEYS[1]
            local now    = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit  = tonumber(ARGV[3])
            local member = ARGV[4]

            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
            if redis.call('ZCARD', key) >= limit then
              local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
              return math.floor(tonumber(oldest[2]) + window)
            end
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            return 0
            """, Long.class);

    /**
     * How long a caller is turned away when the counters cannot be read.
     *
     * <p>Not a window boundary and not pretending to be one: when Redis is
     * unreachable there is no window to report the edge of. It is a backoff,
     * short enough that a blip does not lock anyone out for the hour the IP
     * layer would otherwise imply.
     */
    private static final Duration UNAVAILABLE_BACKOFF = Duration.ofSeconds(60);

    private static final String PREFIX = "ratelimit:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    RateLimiter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    /**
     * Takes a slot in one window, or says when the next one frees up.
     *
     * <p><strong>Refuses when Redis cannot answer.</strong> Failing open would
     * remove the only brake on an endpoint that sends mail on a stranger's
     * say-so, and the sending domain's reputation is not recoverable the way a
     * minute of refusals is. It costs nothing a working deployment notices
     * either: the session lives in the same Redis, so an instance that cannot
     * reach it cannot sign anybody in regardless.
     *
     * @param layer  which of Bolum 40.5's three counters this is, for the key
     *               and for the log line
     * @param subject what is being counted — an address is hashed by the caller,
     *                never passed in clear
     */
    public RateLimitDecision check(String layer, String subject, int limit, Duration window) {
        Instant now = clock.instant();
        long resetsAtMillis;
        try {
            Long answer = redis.execute(SLIDING_WINDOW,
                    List.of(PREFIX + layer + ":" + subject),
                    String.valueOf(now.toEpochMilli()),
                    String.valueOf(window.toMillis()),
                    String.valueOf(limit),
                    randomMember());
            resetsAtMillis = answer == null ? 0L : answer;
        } catch (Exception unavailable) {
            log.warn("Rate limit counters unreachable, refusing on the {} layer: {}",
                    layer, unavailable.getClass().getSimpleName());
            return RateLimitDecision.refusedUntil(now.plus(UNAVAILABLE_BACKOFF));
        }
        if (resetsAtMillis == 0L) {
            return RateLimitDecision.admit();
        }
        // The subject is a hash or an address the operator already routes on,
        // never anything the user wrote (absolute rule 4).
        log.info("Rate limit reached on the {} layer", layer);
        return RateLimitDecision.refusedUntil(Instant.ofEpochMilli(resetsAtMillis));
    }

    /**
     * The key an address is counted under.
     *
     * <p>Hashed, because a Redis key is not a private place: {@code KEYS},
     * {@code MONITOR} and the slow log all show it, and an operator debugging
     * a limiter has no business reading who asked to sign in. The digest is
     * stable, so the same address is the same bucket.
     */
    public static String subjectOf(String address) {
        try {
            return ENCODER.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(address.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every JRE", impossible);
        }
    }

    private static String randomMember() {
        byte[] value = new byte[9];
        RANDOM.nextBytes(value);
        return ENCODER.encodeToString(value);
    }
}
