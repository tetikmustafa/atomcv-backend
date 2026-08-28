package com.mustafatetik.atomcv.email;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Whether a webhook delivery really came from Resend.
 *
 * <p>This is the whole security of the endpoint. It is unauthenticated by
 * necessity — a webhook has no session — so anything that reaches it and is
 * believed can suppress an address, and suppressing an address stops that
 * person signing in. A forged {@code email.bounced} for somebody else's
 * address is a denial of service on one account, delivered by us.
 *
 * <p>Resend signs with Svix's scheme: the signed content is
 * {@code {id}.{timestamp}.{body}}, the key is the base64 body of the
 * {@code whsec_} secret, and the header carries one or more space-separated
 * {@code v1,<base64>} pairs — more than one while a secret is being rotated.
 *
 * <p>Two checks and not one. The signature says the body was not altered; the
 * timestamp says this delivery is not one somebody recorded and replayed later.
 * Without the second, a captured request stays valid forever.
 */
final class ResendSignature {

    /** Svix's own tolerance. Wide enough for clock skew, short enough to matter. */
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SECRET_PREFIX = "whsec_";

    private ResendSignature() {
    }

    /**
     * @param secret    the endpoint secret from the Resend dashboard, with or
     *                  without its {@code whsec_} prefix
     * @param id        the {@code svix-id} header
     * @param timestamp the {@code svix-timestamp} header, seconds since the epoch
     * @param signature the {@code svix-signature} header, one or more pairs
     * @param body      the request body <strong>exactly as it arrived</strong>.
     *                  A parsed and re-serialised body hashes differently, which
     *                  is why the controller takes a String
     */
    static boolean valid(String secret, String id, String timestamp, String signature,
            String body, Instant now) {

        if (isBlank(secret) || isBlank(id) || isBlank(timestamp) || isBlank(signature)
                || body == null) {
            return false;
        }
        if (!fresh(timestamp, now)) {
            return false;
        }

        byte[] expected = sign(secret, id + "." + timestamp + "." + body);
        if (expected == null) {
            return false;
        }

        // Several are offered during a rotation, and any one matching is a
        // valid delivery. Compared with MessageDigest.isEqual, which does not
        // return early on the first differing byte -- a comparison that did
        // would leak the signature one byte at a time to a caller with a clock.
        for (String candidate : signature.split(" ")) {
            int comma = candidate.indexOf(',');
            if (comma < 0 || !candidate.startsWith("v1,")) {
                continue;
            }
            byte[] offered = decode(candidate.substring(comma + 1));
            if (offered != null && MessageDigest.isEqual(expected, offered)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fresh(String timestamp, Instant now) {
        try {
            Instant sent = Instant.ofEpochSecond(Long.parseLong(timestamp.trim()));
            return Duration.between(sent, now).abs().compareTo(TOLERANCE) <= 0;
        } catch (NumberFormatException notATimestamp) {
            return false;
        }
    }

    private static byte[] sign(String secret, String content) {
        try {
            String material = secret.startsWith(SECRET_PREFIX)
                    ? secret.substring(SECRET_PREFIX.length())
                    : secret;
            byte[] key = decode(material);
            if (key == null) {
                return null;
            }
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException impossible) {
            // HmacSHA256 is required of every JVM; an empty key is not.
            return null;
        }
    }

    private static byte[] decode(String base64) {
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
