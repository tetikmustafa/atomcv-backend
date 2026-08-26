package com.mustafatetik.atomcv.identity.domain;

/**
 * How the person behind a session proved who they are (Bolum 40).
 *
 * <p>Declared whole even though Adim 3.3's first slice can only produce
 * {@link #LOCAL_DEV}: a session lives in Redis for as long as its TTL, so a
 * value added later would be read back by a running deployment holding
 * sessions written before the deployment. Widening a closed vocabulary after
 * the fact is the cheap half; the values themselves are already fixed by
 * Bolum 40.2 and 40.6.
 */
public enum AuthMethod {

    /**
     * Bolum 40.6, in the order that section implements them.
     *
     * <p>LinkedIn was the third and is gone: it is the only one of the three
     * that requires a verified company page before an app can exist at all,
     * and the sign-in it buys is the same sign-in the other two already give.
     * {@code V2} narrows the column to match, so the database and this
     * vocabulary cannot drift apart.
     */
    OAUTH_GOOGLE,
    OAUTH_GITHUB,

    /** Bolum 40.2's selector/verifier link. */
    MAGIC_LINK,

    /**
     * The {@code local} profile's shortcut, and the only value the first slice
     * can write. It cannot be produced outside that profile — the endpoint
     * that mints it does not exist elsewhere.
     */
    LOCAL_DEV
}
