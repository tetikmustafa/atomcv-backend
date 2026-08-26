package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.identity.domain.AuthMethod;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.shared.security.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * The store against a real Redis: what it keeps, what it throws away, and the
 * sliding TTL of EK D.6.6.
 *
 * <p>In {@code identity.service} so that the store can be built by hand, which
 * this needs: the clock is the thing under test and the refresh threshold is
 * five minutes, so the application's forward-running clock would make two of
 * these cases untestable and the rest slow.
 */
class SessionStoreIT extends AbstractIntegrationTest {

    private static final Instant NOON = Instant.parse("2026-08-26T12:00:00Z");

    private static final Duration TTL = Duration.ofDays(30);

    private static final Duration TOUCH_AFTER = Duration.ofMinutes(5);

    private static final Duration AGED = Duration.ofHours(1);

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ObjectMapper json;

    @Test
    void aSessionSurvivesTheRoundTripThroughRedisUnchanged() {
        SessionStore store = storeAt(NOON);

        Session created = store.create(UUID.randomUUID(), UserRole.USER, AuthMethod.OAUTH_GOOGLE);

        assertThat(store.find(created.id())).contains(created);
    }

    @Test
    void itIsWrittenWithTheConfiguredExpiry() {
        Session created = storeAt(NOON)
                .create(UUID.randomUUID(), UserRole.USER, AuthMethod.MAGIC_LINK);

        assertThat(expiryOf(created)).isCloseTo(TTL.toSeconds(), Offset.offset(60L));
    }

    /**
     * EK D.6.6: the TTL slides with activity. Bolum 9's "two hours later" was
     * corrected to "two hours after the last activity" for this reason — an
     * absolute expiry cuts off a user who is still working, which is exactly
     * the lost effort design principle 8 exists to prevent.
     */
    @Test
    void activityAfterTheThresholdRefreshesTheExpiryAndTheLastSeenTime() {
        Session created = storeAt(NOON)
                .create(UUID.randomUUID(), UserRole.USER, AuthMethod.MAGIC_LINK);
        // Aged deliberately, so a refresh shows as a rise rather than as noise.
        redis.expire(keyOf(created), AGED);

        Session seen = storeAt(NOON.plus(TOUCH_AFTER).plusSeconds(1))
                .find(created.id())
                .orElseThrow();

        assertThat(seen.lastSeenAt()).isAfter(created.lastSeenAt());
        assertThat(seen.createdAt()).isEqualTo(created.createdAt());
        assertThat(expiryOf(created)).isGreaterThan(AGED.toSeconds());
    }

    /**
     * The other side of the same trade: a browser making ten calls a second
     * must not put ten writes a second behind them.
     */
    @Test
    void activityInsideTheThresholdCostsNoWrite() {
        Session created = storeAt(NOON)
                .create(UUID.randomUUID(), UserRole.USER, AuthMethod.MAGIC_LINK);
        redis.expire(keyOf(created), AGED);

        Session seen = storeAt(NOON.plusSeconds(30)).find(created.id()).orElseThrow();

        assertThat(seen.lastSeenAt()).isEqualTo(created.lastSeenAt());
        assertThat(expiryOf(created)).isLessThanOrEqualTo(AGED.toSeconds());
    }

    @Test
    void revokingOneLeavesTheOthersAlone() {
        SessionStore store = storeAt(NOON);
        UUID user = UUID.randomUUID();
        Session first = store.create(user, UserRole.USER, AuthMethod.OAUTH_GOOGLE);
        Session second = store.create(user, UserRole.USER, AuthMethod.OAUTH_GITHUB);

        store.revoke(first.id());

        assertThat(store.find(first.id())).isEmpty();
        assertThat(store.find(second.id())).isPresent();
    }

    /**
     * What Bolum 40.1 promises over a JWT, and the reason the store keeps a
     * second key per user. Without that index this operation could not exist
     * and "iptal: aninda" would be a claim with nothing behind it — which is
     * also why the index is written from the first session rather than added
     * later, when every session already minted would be unreachable.
     */
    @Test
    void everyBrowserAUserIsSignedInOnCanBeRevokedAtOnce() {
        SessionStore store = storeAt(NOON);
        UUID user = UUID.randomUUID();
        Session laptop = store.create(user, UserRole.USER, AuthMethod.OAUTH_GOOGLE);
        Session phone = store.create(user, UserRole.USER, AuthMethod.MAGIC_LINK);
        Session stranger = store.create(UUID.randomUUID(), UserRole.USER, AuthMethod.MAGIC_LINK);

        assertThat(store.revokeAllFor(user)).isEqualTo(2);

        assertThat(store.find(laptop.id())).isEmpty();
        assertThat(store.find(phone.id())).isEmpty();
        assertThat(store.find(stranger.id())).isPresent();
    }

    /**
     * A field added to {@link Session} makes every session written before it
     * unreadable. That is a sign-out for those browsers, not a 500 for them.
     */
    @Test
    void aStoredValueThatNoLongerFitsTheRecordIsReadAsNoSession() {
        redis.opsForValue().set("sess:not-a-session", "{\"nothing\":1}", TTL);

        assertThat(storeAt(NOON).find("not-a-session")).isEmpty();
    }

    @Test
    void aBlankCookieValueNeverReachesRedis() {
        SessionStore store = storeAt(NOON);

        assertThat(store.find("")).isEmpty();
        assertThat(store.find(null)).isEmpty();
    }

    private SessionStore storeAt(Instant now) {
        return new SessionStore(redis, json,
                new SessionProperties(TTL, TOUCH_AFTER, null, null, true),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private long expiryOf(Session session) {
        Long seconds = redis.getExpire(keyOf(session), TimeUnit.SECONDS);
        assertThat(seconds).isNotNull();
        return seconds;
    }

    private static String keyOf(Session session) {
        return "sess:" + session.id();
    }
}
