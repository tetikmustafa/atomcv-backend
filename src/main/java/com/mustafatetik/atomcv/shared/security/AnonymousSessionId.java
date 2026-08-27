package com.mustafatetik.atomcv.shared.security;

import java.util.Objects;

/**
 * The id of a session nobody has signed in to (Adim 3.6).
 *
 * <p><strong>A type rather than a String, and that is the whole of it.</strong>
 * § 41.3 asks that the scope carry enough type for "went to the wrong store"
 * to be caught rather than debugged, and {@link ProfileRef#ephemeral} needs one
 * fact before it may mint an ephemeral scope: that the session really is
 * anonymous. {@code shared} may not depend on a business module (Bolum 10.2,
 * rule 4), so it cannot look at a {@code Session} and check — but it can refuse
 * to take anything except a value only the module that <em>can</em> check is
 * able to produce.
 *
 * <p>Which is why this is not a public constructor. {@code identity} makes one
 * from a session it has already established is anonymous, and nothing else has
 * a reason to. A caller holding a plain String cannot reach an ephemeral scope
 * by accident, and the compiler is what says so.
 */
public final class AnonymousSessionId {

    private final String value;

    private AnonymousSessionId(String value) {
        this.value = value;
    }

    /**
     * @param sessionId the session's own id, which the caller has established
     *                  belongs to a session with no user on it
     */
    public static AnonymousSessionId of(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("An anonymous session has an id");
        }
        return new AnonymousSessionId(sessionId);
    }

    public String value() {
        return value;
    }

    /**
     * Shape only. A session id is a credential — it is the cookie — and a
     * value that printed itself would put one in the first log line somebody
     * added while debugging.
     */
    @Override
    public String toString() {
        return "AnonymousSessionId[len=" + value.length() + "]";
    }
}
