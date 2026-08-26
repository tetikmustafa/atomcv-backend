package com.mustafatetik.atomcv.identity.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * The {@code state} parameter against a real Redis (Bolum 40.6).
 *
 * <p>In {@code identity.oauth} so the store can be built with a fixed clock,
 * and because {@code redeem} is the package's own contract.
 */
class OAuthStateStoreIT extends AbstractIntegrationTest {

    private static final Instant NOON = Instant.parse("2026-08-26T12:00:00Z");

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ObjectMapper json;

    @Test
    void aStateSurvivesTheRoundTripAndCarriesItsReturnPath() {
        OAuthStateStore store = storeAt(NOON);

        String state = store.begin(OAuthProvider.GOOGLE, "/profile");

        assertThat(store.redeem(state, OAuthProvider.GOOGLE)).contains("/profile");
    }

    /**
     * The property a cookie-only double submit cannot give: a callback URL
     * that reaches a history file, a proxy log or a shared screen is worthless
     * the second time.
     */
    @Test
    void aStateCanBeRedeemedExactlyOnce() {
        OAuthStateStore store = storeAt(NOON);
        String state = store.begin(OAuthProvider.GITHUB, "/");

        assertThat(store.redeem(state, OAuthProvider.GITHUB)).isPresent();
        assertThat(store.redeem(state, OAuthProvider.GITHUB)).isEmpty();
    }

    /** Nothing legitimate mints a state at one provider and spends it at another. */
    @Test
    void aStateMintedForOneProviderIsNotRedeemableAtAnother() {
        OAuthStateStore store = storeAt(NOON);
        String state = store.begin(OAuthProvider.GOOGLE, "/");

        assertThat(store.redeem(state, OAuthProvider.GITHUB)).isEmpty();
    }

    /**
     * And the refusal consumed it. A forged callback must not be able to
     * probe a state at one provider and then use it at the right one.
     */
    @Test
    void aRefusalAtTheWrongProviderStillSpendsTheState() {
        OAuthStateStore store = storeAt(NOON);
        String state = store.begin(OAuthProvider.GOOGLE, "/");

        store.redeem(state, OAuthProvider.GITHUB);

        assertThat(store.redeem(state, OAuthProvider.GOOGLE)).isEmpty();
    }

    @Test
    void aStateNobodyMintedIsRefused() {
        OAuthStateStore store = storeAt(NOON);

        assertThat(store.redeem("not-a-state", OAuthProvider.GOOGLE)).isEmpty();
        assertThat(store.redeem("", OAuthProvider.GOOGLE)).isEmpty();
        assertThat(store.redeem(null, OAuthProvider.GOOGLE)).isEmpty();
    }

    /** An abandoned consent screen is not a credential lying around for a day. */
    @Test
    void itExpiresOnItsOwn() {
        OAuthStateStore store = storeAt(NOON);

        String state = store.begin(OAuthProvider.GOOGLE, "/");

        Long seconds = redis.getExpire("oauth:state:" + state, TimeUnit.SECONDS);
        assertThat(seconds).isNotNull()
                .isCloseTo(OAuthStateStore.TTL.toSeconds(), Offset.offset(30L));
        assertThat(OAuthStateStore.TTL).isLessThanOrEqualTo(Duration.ofMinutes(10));
    }

    /**
     * The record is package-private and Jackson reaches it reflectively. If
     * that ever stopped working the failure would be a sign-in that always
     * lands on the error page, so it is asserted rather than assumed.
     */
    @Test
    void theStoredValueIsReadableBackIntoItsRecord() {
        OAuthStateStore store = storeAt(NOON);
        String state = store.begin(OAuthProvider.GITHUB, "/generate/new");

        String raw = redis.opsForValue().get("oauth:state:" + state);

        // The enum serialises as its constant name; the wire value belongs to
        // the database column and the URL, not to this internal record.
        assertThat(raw).contains("GITHUB").contains("/generate/new");
        assertThat(store.redeem(state, OAuthProvider.GITHUB)).contains("/generate/new");
    }

    private OAuthStateStore storeAt(Instant now) {
        return new OAuthStateStore(redis, json, Clock.fixed(now, ZoneOffset.UTC));
    }
}
