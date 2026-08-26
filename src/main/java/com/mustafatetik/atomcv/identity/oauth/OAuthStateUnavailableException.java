package com.mustafatetik.atomcv.identity.oauth;

/**
 * The state could not be stored, so the round trip must not begin.
 *
 * <p>Unchecked and unhandled: it reaches the generic 500. Sending the user to
 * a consent screen whose callback is already guaranteed to fail wastes their
 * time and teaches them the product is broken in a way a 500 does not.
 */
public class OAuthStateUnavailableException extends RuntimeException {

    OAuthStateUnavailableException(Throwable cause) {
        super("Could not store the OAuth state", cause);
    }
}
