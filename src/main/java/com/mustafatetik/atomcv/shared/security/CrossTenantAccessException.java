package com.mustafatetik.atomcv.shared.security;

/**
 * A write was attempted against a row that belongs to someone else.
 *
 * <p>Reads do not throw: a foreign row reads as absent, so a caller cannot tell
 * "not yours" apart from "does not exist" and cannot probe for the existence of
 * other people's data. A write is different — reaching one means the code
 * assembled an object with the wrong owner, which is a defect, not a request to
 * answer politely.
 *
 * <p>The message names no identifier. There is nothing useful to say about
 * another tenant's row.
 */
public class CrossTenantAccessException extends RuntimeException {

    public CrossTenantAccessException(String message) {
        super(message);
    }
}
