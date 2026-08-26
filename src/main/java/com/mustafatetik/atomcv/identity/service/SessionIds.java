package com.mustafatetik.atomcv.identity.service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Session identifiers, and nothing else that could be mistaken for one.
 *
 * <p>256 bits from {@link SecureRandom}. The value is the whole credential —
 * anyone holding it is the user — so it is neither derived from the account
 * nor sequential, and it is not a {@code UUID}: a v4 UUID carries 122 random
 * bits, and more to the point a UUID-shaped session id invites code to treat
 * it as a database key.
 */
final class SessionIds {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final int BYTES = 32;

    private SessionIds() {
    }

    static String next() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
