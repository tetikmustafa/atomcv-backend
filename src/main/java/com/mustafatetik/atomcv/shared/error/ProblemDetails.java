package com.mustafatetik.atomcv.shared.error;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.ProblemDetail;

/**
 * Turns a {@link UserFacingError} into the RFC 7807 body of Bolum 35.4.
 *
 * <p>{@code title} and {@code type} are derived from the code rather than
 * listed separately. RFC 7807 wants a title that is stable across occurrences,
 * and a second list to maintain is a list that drifts.
 */
public final class ProblemDetails {

    /**
     * Relative on purpose. Bolum 35.4's example uses the production domain, but
     * the product document requires that neither the name nor the domain is
     * baked into code (EK C.5) — and RFC 7807 allows a relative reference.
     */
    private static final String TYPE_PREFIX = "/errors/";

    private ProblemDetails() {
    }

    public static ProblemDetail from(UserFacingError error) {
        ProblemDetail problem = ProblemDetail.forStatus(error.httpStatus());
        problem.setType(URI.create(TYPE_PREFIX + slug(error.code())));
        problem.setTitle(title(error.code()));
        problem.setProperty("code", error.code().name());
        if (!error.params().isEmpty()) {
            problem.setProperty("params", error.params());
        }
        if (!error.resolutions().isEmpty()) {
            problem.setProperty("resolutions", error.resolutions());
        }
        return problem;
    }

    /** {@code CONFLICTING_PREFERENCES} to {@code conflicting-preferences}. */
    static String slug(ErrorCode code) {
        return code.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Developer-facing English, never displayed (EK D.6.2). The frontend
     * resolves {@code errors.{CODE}} in the user's language; this exists so a
     * log line and a debugging session have a sentence to read.
     */
    static String title(ErrorCode code) {
        String words = code.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
