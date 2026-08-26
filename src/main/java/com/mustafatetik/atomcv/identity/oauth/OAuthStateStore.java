package com.mustafatetik.atomcv.identity.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * The {@code state} parameter Bolum 40.6 makes mandatory, kept where the
 * client cannot reach it.
 *
 * <p>Bolum 40.6's snippet puts it in a servlet session. There isn't one — the
 * chain is stateless by design (Bolum 40.1) — so it lives in Redis under the
 * state value itself, which is strictly better than the cookie alternative:
 * <strong>redemption is a single atomic read-and-delete</strong>, so a
 * callback URL replayed from a history file, a proxy log or a shared screen is
 * refused the second time. A cookie-only double submit cannot do that.
 *
 * <p>The provider is stored alongside, so a state minted for one provider
 * cannot be redeemed at another's callback.
 */
@Component
public class OAuthStateStore {

    /**
     * Long enough for a slow consent screen, short enough that an abandoned
     * one is not a credential lying around. Bolum 40.2 gives the magic link
     * ten minutes for the same reason.
     */
    static final Duration TTL = Duration.ofMinutes(10);

    private static final String PREFIX = "oauth:state:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Logger log = LoggerFactory.getLogger(OAuthStateStore.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Clock clock;

    OAuthStateStore(StringRedisTemplate redis, ObjectMapper json, Clock clock) {
        this.redis = redis;
        this.json = json;
        this.clock = clock;
    }

    /**
     * @return the opaque value to put in the authorization URL
     * @throws OAuthStateUnavailableException when it could not be stored —
     *                                        starting a round trip whose state
     *                                        was never kept sends the user to
     *                                        a consent screen that cannot
     *                                        succeed
     */
    public String begin(OAuthProvider provider, String returnTo) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String state = ENCODER.encodeToString(bytes);
        try {
            redis.opsForValue().set(
                    PREFIX + state,
                    json.writeValueAsString(new OAuthState(provider, returnTo, clock.instant())),
                    TTL);
            return state;
        } catch (Exception unavailable) {
            throw new OAuthStateUnavailableException(unavailable);
        }
    }

    /**
     * Redeems the state, once, and answers where to send the person afterwards.
     *
     * <p>Empty means the callback is not one we started: forged, expired,
     * already used, or redeemed at a provider it was not minted for.
     *
     * <p>Returns the return path rather than the record, so the only thing
     * that escapes this package is the one value the caller needs — and
     * {@code startedAt} cannot be mistaken for an expiry check that Redis is
     * already performing.
     */
    public Optional<String> redeem(String state, OAuthProvider calledBack) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        try {
            String stored = redis.opsForValue().getAndDelete(PREFIX + state);
            if (stored == null) {
                return Optional.empty();
            }
            OAuthState begun = json.readValue(stored, OAuthState.class);
            if (begun.provider() != calledBack) {
                // A state minted at Google's start redeemed at GitHub's
                // callback. Nothing legitimate does this.
                log.warn("OAuth state redeemed at the wrong provider's callback");
                return Optional.empty();
            }
            return Optional.of(begun.returnTo());
        } catch (Exception unavailableOrStale) {
            log.warn("OAuth state lookup failed, refusing the callback: {}",
                    unavailableOrStale.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
