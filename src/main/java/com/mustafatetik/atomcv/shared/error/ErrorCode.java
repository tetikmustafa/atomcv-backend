package com.mustafatetik.atomcv.shared.error;

import static com.mustafatetik.atomcv.shared.error.ParamType.BOOLEAN;
import static com.mustafatetik.atomcv.shared.error.ParamType.INTEGER;
import static com.mustafatetik.atomcv.shared.error.ParamType.NUMBER;
import static com.mustafatetik.atomcv.shared.error.ParamType.STRING;
import static com.mustafatetik.atomcv.shared.error.ParamType.STRING_ARRAY;
import static com.mustafatetik.atomcv.shared.error.ParamType.TIMESTAMP;
import static com.mustafatetik.atomcv.shared.error.ParamType.UUID_VALUE;

import java.util.List;
import java.util.Optional;

/**
 * Every error the API can name, with the HTTP status it carries and the exact
 * parameters it publishes (Bolum 25.2, 35.5, EK D.6).
 *
 * <p>The server sends this code and these parameters — never a sentence. The
 * frontend resolves {@code errors.{CODE}} in its own language, so a code that
 * arrives without a catalogue entry renders as a raw key to a user. Declaring
 * the parameters here is what lets that catalogue be written.
 *
 * <p><strong>Parameters never carry user content</strong> (absolute rule 4).
 * They carry counts, limits, identifiers and field names — the shape of the
 * problem, not the text that caused it.
 */
public enum ErrorCode {

    // ── Preflight, before any LLM call is made (Bolum 25.2) ──
    INSUFFICIENT_PROFILE(422, param("completeness", INTEGER), param("missing", STRING_ARRAY)),
    UNPARSEABLE_JOB_DESCRIPTION(422, param("confidence", NUMBER), param("skillsFound", INTEGER)),
    CONFLICTING_PREFERENCES(409, param("pinnedPages", NUMBER), param("maxPages", INTEGER)),
    FEATURE_REQUIRES_ACCOUNT(403, param("feature", STRING)),
    QUOTA_EXCEEDED(429, param("metric", STRING), param("resetsAt", TIMESTAMP)),

    // ── Pipeline runtime (Bolum 25.2) ──
    ALL_PROVIDERS_UNAVAILABLE(503, param("tried", STRING_ARRAY)),
    COMPILATION_FAILED(502, param("detail", STRING), param("rawSourceAvailable", BOOLEAN)),
    PAGE_LIMIT_EXCEEDED(422, param("actual", INTEGER), param("limit", INTEGER)),
    REWRITE_VALIDATION_FAILED(500, param("atomId", UUID_VALUE), param("issues", STRING_ARRAY)),
    EMBEDDING_UNAVAILABLE(503),

    /**
     * Bolum 44.3's emergency brake is on. No parameters: there is nothing
     * about the request to change, and the user's profile is untouched — the
     * brake stops generation and not access.
     */
    GENERATION_PAUSED(503),

    // ── Ingestion (Bolum 31.10, coded in EK D.6) ──
    PDF_NOT_TEXT_BASED(422),
    PDF_ENCRYPTED(422),
    EXTRACTION_EMPTY(422),
    EXTRACTION_TIMEOUT(504),
    LANGUAGE_UNDETECTED(422, param("detectedCandidates", STRING_ARRAY)),
    PROFILE_QUOTA_EXCEEDED(429, param("limit", INTEGER), param("resetsAt", TIMESTAMP)),

    // ── Anonymous mode (EK D.6) ──
    ANONYMOUS_SESSION_EXPIRED(401),
    ATOM_LIMIT_EXCEEDED(422, param("limit", INTEGER), param("current", INTEGER)),
    NO_ANONYMOUS_PROFILE(404),
    PROFILE_ALREADY_EXISTS(409),

    // ── Artifacts and sessions (EK D.6) ──
    GENERATION_ARTIFACT_EXPIRED(410),
    CSRF_TOKEN_INVALID(403),

    // ── CRUD and the catch-all, added in Adim 1.2 ──
    RESOURCE_NOT_FOUND(404),
    VERSION_CONFLICT(412),
    PRECONDITION_REQUIRED(428),
    VALIDATION_FAILED(400, param("fields", STRING_ARRAY)),
    INTERNAL_ERROR(500),

    // ── Protocol-level rejections (EK D.6.8). A correct client never sees
    // these; they exist so that a malformed request is answered as the
    // client's mistake rather than as a server failure.
    METHOD_NOT_ALLOWED(405),
    NOT_ACCEPTABLE(406),
    UNSUPPORTED_MEDIA_TYPE(415);

    /** One published parameter: the key the frontend reads, and its JSON type. */
    public record Param(String name, ParamType type) {

        public Param {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("A parameter needs a name");
            }
            if (type == null) {
                throw new IllegalArgumentException("Parameter " + name + " needs a type");
            }
        }
    }

    private final int httpStatus;
    private final List<Param> params;

    ErrorCode(int httpStatus, Param... params) {
        this.httpStatus = httpStatus;
        this.params = List.of(params);
    }

    private static Param param(String name, ParamType type) {
        return new Param(name, type);
    }

    public int httpStatus() {
        return httpStatus;
    }

    /** The complete set this code publishes. Empty means the code carries none. */
    public List<Param> params() {
        return params;
    }

    public Optional<Param> param(String name) {
        return params.stream().filter(param -> param.name().equals(name)).findFirst();
    }
}
