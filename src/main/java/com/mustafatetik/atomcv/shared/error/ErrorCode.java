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
    /**
     * One code, eight reasons. {@code reason} is the closed vocabulary of
     * {@link UnreadablePostingReason}; {@code confidence} and
     * {@code skillsFound} describe two of the eight and are zero from the
     * preflight, so the message is chosen on {@code reason} first (F-016).
     */
    UNPARSEABLE_JOB_DESCRIPTION(422,
            param("reason", STRING), param("confidence", NUMBER), param("skillsFound", INTEGER)),
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

    /**
     * The file is not one of the five Bolum 31.3 can read (Adim 3.4, Ekleme).
     *
     * <p>EK D.6 codes Bolum 31.10's table, which starts after a file has been
     * accepted; the two rungs before it had no codes. This is the first —
     * raised for an extension we do not read, for a declared media type that
     * contradicts the extension, and for bytes that contradict both. One code
     * for all three: what the user does about it is the same in every case,
     * and a renamed file and an unsupported one are not worth telling apart in
     * a sentence that has to end "upload a PDF, DOCX, TEX, TXT or MD".
     *
     * <p>{@code accepted} is published rather than hardcoded in the client so
     * the list has one owner. A format added here reaches the file picker's
     * message without a frontend release.
     */
    UNSUPPORTED_DOCUMENT(415, param("accepted", STRING_ARRAY)),

    /**
     * Over Bolum 42.1's ten megabytes (Adim 3.4, Ekleme).
     *
     * <p>Publishes the limit and not the size that was sent. The client knows
     * what it uploaded and the server's own reading of it is unreliable at the
     * point this is usually raised — Spring refuses an oversized multipart
     * before the bytes are counted, and a number that is sometimes right is
     * worse in an error than no number.
     */
    DOCUMENT_TOO_LARGE(413, param("limitBytes", INTEGER)),

    PDF_NOT_TEXT_BASED(422),
    PDF_ENCRYPTED(422),
    EXTRACTION_EMPTY(422),
    EXTRACTION_TIMEOUT(504),
    LANGUAGE_UNDETECTED(422, param("detectedCandidates", STRING_ARRAY)),

    /**
     * A wording could not be regenerated in another language (Adim 3.5).
     *
     * <p>Parameterless, and it names no sentence: what a translation dropped
     * is the user's own content (absolute rule 4). It is also barely a
     * user-facing code — this arrives on a background job, and what the person
     * actually sees is the wording still marked stale, which is the true
     * statement about it. The code exists so that {@code GET /jobs/{id}}
     * answers something better than "internal error" to a client that asks.
     */
    TRANSLATION_FAILED(422),
    PROFILE_QUOTA_EXCEEDED(429, param("limit", INTEGER), param("resetsAt", TIMESTAMP)),

    // ── Anonymous mode (EK D.6) ──
    ANONYMOUS_SESSION_EXPIRED(401),
    ATOM_LIMIT_EXCEEDED(422, param("limit", INTEGER), param("current", INTEGER)),
    NO_ANONYMOUS_PROFILE(404),
    PROFILE_ALREADY_EXISTS(409),

    // ── Artifacts and sessions (EK D.6) ──
    GENERATION_ARTIFACT_EXPIRED(410),
    CSRF_TOKEN_INVALID(403),

    /**
     * No session at all on a request that needs one (Adim 3.3, Ekleme).
     *
     * <p>EK D.6 names {@link #ANONYMOUS_SESSION_EXPIRED} for a session that ran
     * out and {@link #FEATURE_REQUIRES_ACCOUNT} for a feature an anonymous
     * user cannot reach, but nothing for the plain case of a request arriving
     * with no {@code sid} cookie. Reusing the expiry code would have the
     * server claim a session existed and lapsed, which is a sentence the user
     * reads and a diagnosis the logs cannot correct later.
     *
     * <p>Adim 3.6 mints an anonymous session for a caller without a cookie, so
     * this becomes rare rather than wrong: it stays the answer for a request
     * that reaches a user-scoped endpoint carrying nothing.
     */
    AUTHENTICATION_REQUIRED(401),

    /**
     * A sign-in that did not happen (Adim 3.3, Bolum 40.6).
     *
     * <p>One code with a closed {@code reason} rather than seven codes, which
     * is the shape F-016 asked for: the frontend resolves one ICU key with a
     * {@code select}, and a reason added later lands in its {@code other}
     * branch instead of rendering a raw key to a user. The vocabulary is
     * {@code identity.oauth.OAuthFailure}.
     *
     * <p><strong>It usually arrives as a query parameter, not as a body.</strong>
     * Both OAuth endpoints are browser navigations, so a failure is a redirect
     * to the frontend's error route carrying this code and its reason. The
     * status is what it would be if anything ever asked for it as JSON.
     */
    OAUTH_FAILED(400, param("reason", STRING)),

    /**
     * A sign-in link that will not be redeemed (Bolum 40.2).
     *
     * <p><strong>No parameters, and deliberately no reason.</strong> Everywhere
     * else in this catalogue a closed vocabulary is the better shape; here it
     * is the vulnerability. Expired, already used, wrong verifier and never
     * existed have to be one answer, because distinguishing them tells someone
     * guessing which half of the guess was right.
     */
    MAGIC_LINK_INVALID(400),

    /**
     * Too many sign-in requests, from this address or this caller (Bolum 40.5).
     *
     * <p><strong>Which of the three layers refused is not published.</strong>
     * The sentence a user reads is the same either way — wait, then try again
     * — and naming the global layer would tell somebody probing the service
     * that their traffic is landing. The layer reaches the operator through a
     * log line instead.
     *
     * <p>It publishes {@code resetsAt} and nothing else, and that is not a
     * courtesy: {@code ProblemDetailAdvice} derives {@code Retry-After} from
     * it, which is the only one of the two that is right when the client's
     * clock is wrong.
     *
     * <p>It does not answer Bolum 40.4's question either. The address layer can
     * only refuse a caller who already spent that window themselves, so what
     * comes back describes what they did, never whether the address has an
     * account.
     */
    RATE_LIMITED(429, param("resetsAt", TIMESTAMP)),

    /**
     * The bot check did not pass (Bolum 44.4).
     *
     * <p><strong>Named for what it is, not for who provides it.</strong> The
     * same reasoning that made the observability variables {@code OTLP_*}
     * rather than {@code AXIOM_*}: this code is rendered by a frontend and
     * stored in its message catalogue, and leaving Cloudflare should not turn
     * a user-facing string into a lie.
     *
     * <p>No parameters. A token that was missing, expired, already spent or
     * forged all leave the client with the same single thing to do — reset the
     * widget and ask again — so a reason would be a vocabulary nobody
     * branches on.
     */
    CHALLENGE_FAILED(403),

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
