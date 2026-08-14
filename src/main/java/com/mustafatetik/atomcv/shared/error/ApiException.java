package com.mustafatetik.atomcv.shared.error;

import java.util.Objects;

/**
 * An expected failure on its way out of the API, carrying the body the user
 * will see.
 *
 * <p>Expected failure paths inside the generation pipeline use {@code Result}
 * (Bolum 25.1) rather than exceptions. This is for the layer above: a request
 * that cannot be served, where the alternative would be threading a Result
 * through every controller signature for a case that always ends the request.
 *
 * <p>No stack trace is filled in — the body is the point, not the trace, and
 * these are thrown often enough on ordinary paths (a 404, a stale ETag) that
 * collecting one is waste.
 */
public class ApiException extends RuntimeException {

    private final transient UserFacingError error;

    public ApiException(UserFacingError error) {
        super(Objects.requireNonNull(error, "error").code().name(), null, false, false);
        this.error = error;
    }

    public static ApiException of(ErrorCode code, Resolution... resolutions) {
        return new ApiException(UserFacingError.of(code, resolutions));
    }

    public UserFacingError error() {
        return error;
    }
}
