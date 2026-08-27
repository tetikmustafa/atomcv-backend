package com.mustafatetik.atomcv.profile.service;

/**
 * Redis could not answer for an anonymous profile (Adim 3.6).
 *
 * <p>Thrown rather than answered as an absence, and the difference matters
 * here more than anywhere: an anonymous profile has no second home, so an
 * empty answer would tell somebody who just uploaded their CV that they have
 * not uploaded anything — and they would upload it again, against a store that
 * is still down.
 */
public class EphemeralProfileUnavailableException extends RuntimeException {

    public EphemeralProfileUnavailableException(Throwable cause) {
        super("The anonymous profile store did not answer", cause);
    }
}
