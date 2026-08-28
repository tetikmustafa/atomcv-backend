package com.mustafatetik.atomcv.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * The whole security of an unauthenticated endpoint (Bolum 40.2).
 *
 * <p>Anything believed at the webhook can suppress an address, and a suppressed
 * address cannot sign in — so a forged {@code email.bounced} is a denial of
 * service on one account, delivered by us. Every case here is a way of getting
 * that wrong.
 */
class ResendSignatureTest {

    private static final String SECRET = "whsec_" + Base64.getEncoder()
            .encodeToString("a-shared-secret".getBytes(StandardCharsets.UTF_8));
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");
    private static final String ID = "msg_2abc";
    private static final String BODY = "{\"type\":\"email.bounced\"}";

    @Test
    void aDeliverySignedWithTheSecretIsAccepted() {
        String stamp = stamp(NOW);

        assertThat(ResendSignature.valid(SECRET, ID, stamp, header(ID, stamp, BODY), BODY, NOW))
                .isTrue();
    }

    /** The prefix is a label on the secret, not part of the key. */
    @Test
    void theSecretWorksWithOrWithoutItsPrefix() {
        String stamp = stamp(NOW);
        String bare = SECRET.substring("whsec_".length());

        assertThat(ResendSignature.valid(bare, ID, stamp, header(ID, stamp, BODY), BODY, NOW))
                .isTrue();
    }

    /** Several are offered while a secret is rotated; any one matching is enough. */
    @Test
    void oneMatchingSignatureAmongSeveralIsEnough() {
        String stamp = stamp(NOW);
        String offered = "v1,c29tZXRoaW5nRWxzZQ== " + header(ID, stamp, BODY);

        assertThat(ResendSignature.valid(SECRET, ID, stamp, offered, BODY, NOW)).isTrue();
    }

    // ── the ways it must refuse ───────────────────────────────────────────

    @Test
    void aBodyChangedAfterSigningIsRefused() {
        String stamp = stamp(NOW);
        String signature = header(ID, stamp, BODY);

        assertThat(ResendSignature.valid(SECRET, ID, stamp, signature,
                "{\"type\":\"email.bounced\",\"data\":{\"to\":[\"victim@example.com\"]}}", NOW))
                .isFalse();
    }

    @Test
    void aSignatureFromADifferentSecretIsRefused() {
        String stamp = stamp(NOW);
        String theirs = header("whsec_" + Base64.getEncoder()
                .encodeToString("not-our-secret".getBytes(StandardCharsets.UTF_8)),
                ID, stamp, BODY);

        assertThat(ResendSignature.valid(SECRET, ID, stamp, theirs, BODY, NOW)).isFalse();
    }

    /**
     * The id is part of the signed content, so replaying one delivery's
     * signature under another id does not verify.
     */
    @Test
    void aSignatureLiftedOntoAnotherMessageIdIsRefused() {
        String stamp = stamp(NOW);
        String signature = header(ID, stamp, BODY);

        assertThat(ResendSignature.valid(SECRET, "msg_other", stamp, signature, BODY, NOW))
                .isFalse();
    }

    /**
     * The reason there are two checks and not one. The signature alone stays
     * valid forever, so a request somebody captured today would still be
     * accepted next year.
     */
    @Test
    void aPerfectlySignedDeliveryFromAnHourAgoIsRefused() {
        Instant old = NOW.minusSeconds(3600);
        String stamp = stamp(old);

        assertThat(ResendSignature.valid(SECRET, ID, stamp, header(ID, stamp, BODY), BODY, NOW))
                .isFalse();
    }

    /** And one from the future, which is the same replay with a clock moved. */
    @Test
    void aDeliveryStampedInTheFutureIsRefused() {
        Instant ahead = NOW.plusSeconds(3600);
        String stamp = stamp(ahead);

        assertThat(ResendSignature.valid(SECRET, ID, stamp, header(ID, stamp, BODY), BODY, NOW))
                .isFalse();
    }

    /** Clock skew is not an attack; five minutes either way is Svix's own tolerance. */
    @Test
    void aDeliveryWithinTheToleranceIsAccepted() {
        Instant skewed = NOW.minusSeconds(120);
        String stamp = stamp(skewed);

        assertThat(ResendSignature.valid(SECRET, ID, stamp, header(ID, stamp, BODY), BODY, NOW))
                .isTrue();
    }

    @Test
    void anythingMissingIsRefusedRatherThanWavedThrough() {
        String stamp = stamp(NOW);
        String signature = header(ID, stamp, BODY);

        assertThat(ResendSignature.valid(SECRET, null, stamp, signature, BODY, NOW)).isFalse();
        assertThat(ResendSignature.valid(SECRET, ID, null, signature, BODY, NOW)).isFalse();
        assertThat(ResendSignature.valid(SECRET, ID, stamp, null, BODY, NOW)).isFalse();
        assertThat(ResendSignature.valid(SECRET, ID, stamp, signature, null, NOW)).isFalse();
        assertThat(ResendSignature.valid("", ID, stamp, signature, BODY, NOW)).isFalse();
    }

    @Test
    void rubbishInTheHeadersIsRefusedRatherThanThrown() {
        String stamp = stamp(NOW);

        assertThat(ResendSignature.valid(SECRET, ID, "not-a-number",
                header(ID, stamp, BODY), BODY, NOW)).isFalse();
        assertThat(ResendSignature.valid(SECRET, ID, stamp, "v1,!!!not-base64!!!", BODY, NOW))
                .isFalse();
        assertThat(ResendSignature.valid(SECRET, ID, stamp, "no-version-prefix", BODY, NOW))
                .isFalse();
    }

    // ── fixtures: the sender's side of the scheme ─────────────────────────

    private static String stamp(Instant at) {
        return String.valueOf(at.getEpochSecond());
    }

    private static String header(String id, String timestamp, String body) {
        return header(SECRET, id, timestamp, body);
    }

    private static String header(String secret, String id, String timestamp, String body) {
        try {
            byte[] key = Base64.getDecoder().decode(secret.substring("whsec_".length()));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] signed = mac.doFinal(
                    (id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(signed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
