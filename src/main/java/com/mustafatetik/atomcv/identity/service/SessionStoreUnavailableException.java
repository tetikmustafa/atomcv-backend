package com.mustafatetik.atomcv.identity.service;

/**
 * The session could not be written, so no cookie may be sent for it.
 *
 * <p>Unchecked and unhandled on purpose: it reaches the generic 500 handler.
 * A login that answers 200 while the store dropped the session is worse than
 * one that fails — the user believes they are signed in and every following
 * request disagrees.
 */
public class SessionStoreUnavailableException extends RuntimeException {

    SessionStoreUnavailableException(Throwable cause) {
        super("Could not write the session", cause);
    }
}
