package com.mustafatetik.atomcv.shared.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.mustafatetik.atomcv.shared.security.CrossTenantAccessException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Every failure leaves the API as the same shape (Bolum 35.4): a code the
 * frontend can translate, the parameters its message needs, and the ways out.
 *
 * <p>Nothing here writes a sentence for the user. A handler that returned
 * English prose would be invisible until a Turkish user hit that path.
 *
 * <p>Handlers return {@link ResponseEntity} rather than a bare
 * {@link ProblemDetail}: the status inside the body does not become the status
 * of the response, so a bare return would answer 500 while the body claimed
 * 409.
 */
@RestControllerAdvice
public class ProblemDetailAdvice {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAdvice.class);

    private final java.time.Clock clock;

    /**
     * The container's own limit, read from the property that enforces it.
     *
     * <p>Not from {@code ingestion}: {@code shared} may not depend on a
     * business module (Bolum 10.2, rule 4), and reading the property is
     * better anyway — it is the number that actually refused the request,
     * rather than a second copy of it.
     */
    private final DataSize maxUploadSize;

    ProblemDetailAdvice(java.time.Clock clock,
            @Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxUploadSize) {
        this.maxUploadSize = maxUploadSize;
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handle(ApiException exception) {
        return respond(exception.error());
    }

    /**
     * A write against someone else's row. Reads already return empty, so no
     * legitimate client can reach this: the code built an object with the wrong
     * owner, which is a defect and is reported as one. The log line carries no
     * identifier — there is nothing useful to say about another tenant's row.
     */
    @ExceptionHandler(CrossTenantAccessException.class)
    public ResponseEntity<ProblemDetail> handle(CrossTenantAccessException exception) {
        log.error("Refused a write against another tenant's row");
        return respond(UserFacingError.of(ErrorCode.INTERNAL_ERROR));
    }

    /** A stale {@code If-Match}: someone else saved first (Bolum 35.6). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handle(ObjectOptimisticLockingFailureException exception) {
        return respond(UserFacingError.of(ErrorCode.VERSION_CONFLICT,
                Resolution.of(ResolutionAction.RETRY)));
    }

    /**
     * Field names only. A rejected value is user content and does not belong in
     * a response body that gets logged, screenshotted and pasted into issues
     * (absolute rule 4).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handle(MethodArgumentNotValidException exception) {
        List<String> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField())
                .distinct()
                .sorted()
                .toList();
        return respond(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                .param("fields", fields)
                .build());
    }

    /**
     * A body the parser could not turn into the request type — a malformed
     * document, a number where a string belongs, or a value a record's
     * constructor refuses. All of those are the client's mistake, and without
     * this they would reach the catch-all and answer 500.
     *
     * <p>Jackson knows which field it choked on; the value it choked on stays
     * out of the response for the same reason validation errors do.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handle(HttpMessageNotReadableException exception) {
        List<String> fields = exception.getCause() instanceof JsonMappingException mapping
                ? mapping.getPath().stream()
                        .map(JsonMappingException.Reference::getFieldName)
                        .filter(Objects::nonNull)
                        .toList()
                : List.of();
        return respond(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                .param("fields", fields)
                .build());
    }

    /**
     * An unknown path. Ordinary enough — a stale bookmark, a typo, a scanner —
     * that letting it reach the catch-all would answer 500 and fill the log
     * with stack traces for something that is not a failure at all.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handle(NoResourceFoundException exception) {
        return respond(UserFacingError.of(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * A path parameter or query parameter that could not be converted, or one
     * the handler needs and the request left out. Both are malformed requests
     * and belong with the other 400s; without these two they reach the
     * catch-all, which answers 500 and tells the user the server broke.
     *
     * <p>Only the parameter's name travels. Its value is user input
     * (absolute rule 4) and is neither logged nor returned.
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemDetail> handleBadParameter(Exception exception) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ((MissingServletRequestParameterException) exception).getParameterName();
        return respond(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                .param("fields", List.of(field))
                .build());
    }

    /**
     * A body sent as a media type no handler consumes. RFC 9110 calls this 415,
     * and it mattered in practice: Bolum 35.6 documented
     * {@code application/merge-patch+json} for the profile patches, which no
     * controller declares, so every client following the specification was told
     * the server had failed (EK D.6.8).
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handle(HttpMediaTypeNotSupportedException exception) {
        return respond(UserFacingError.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    /** An {@code Accept} header no handler can satisfy. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ProblemDetail> handle(HttpMediaTypeNotAcceptableException exception) {
        return respond(UserFacingError.of(ErrorCode.NOT_ACCEPTABLE));
    }

    /**
     * A known path with an unsupported method. The {@code Allow} header is not
     * decoration: RFC 9110 requires it on a 405, and it is what tells a client
     * whether it used the wrong verb or the wrong URL.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handle(HttpRequestMethodNotSupportedException exception) {
        UserFacingError error = UserFacingError.of(ErrorCode.METHOD_NOT_ALLOWED);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(error.httpStatus());
        Set<HttpMethod> allowed = exception.getSupportedHttpMethods();
        if (allowed != null && !allowed.isEmpty()) {
            response.allow(allowed.toArray(HttpMethod[]::new));
        }
        return response.body(ProblemDetails.from(error));
    }

    /**
     * A multipart the container refused before any handler saw it (Adim 3.4).
     *
     * <p>Left alone it reaches the catch-all and leaves as a 500, which tells
     * the user their upload broke the server rather than that it was too
     * large. The size is not published — Spring refuses before the bytes are
     * counted, and the ingestion ladder's own check publishes the same code
     * with the same single parameter, so the two answers are one answer.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handle(MaxUploadSizeExceededException exception) {
        return respond(UserFacingError.with(ErrorCode.DOCUMENT_TOO_LARGE)
                .param("limitBytes", (int) maxUploadSize.toBytes())
                .build());
    }

    /**
     * The catch-all. Without it an unexpected failure would leave as Spring's
     * default body, which carries no {@code code} — and the frontend's error
     * handling is built entirely on that field, so the user would see nothing
     * at all rather than something unhelpful.
     *
     * <p>The throwable is logged in full. It may carry text we would not choose
     * to log, but a production that cannot explain its own 500s is worse.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handle(Exception exception) {
        log.error("Unhandled failure", exception);
        return respond(UserFacingError.of(ErrorCode.INTERNAL_ERROR));
    }

    private ResponseEntity<ProblemDetail> respond(UserFacingError error) {
        var response = ResponseEntity.status(error.httpStatus());
        retryAfterSeconds(error).ifPresent(seconds ->
                response.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds)));
        return response.body(ProblemDetails.from(error));
    }

    /**
     * EK D.6.5: a 429 carries {@code Retry-After} as well as {@code resetsAt}.
     *
     * <p>Both, and they are not redundant. {@code resetsAt} is an absolute
     * instant the client renders in the user's own locale; {@code Retry-After}
     * is a duration, and it is the only one of the two that is right when the
     * client's own clock is wrong — which is exactly the client that would
     * otherwise retry immediately and be refused again.
     *
     * <p>Rounded up and floored at one: a wait of zero seconds is an
     * invitation to retry now, which is what the header exists to prevent.
     */
    private Optional<Long> retryAfterSeconds(UserFacingError error) {
        if (error.httpStatus() != 429) {
            return Optional.empty();
        }
        if (!(error.params().get("resetsAt") instanceof Instant resetsAt)) {
            return Optional.empty();
        }
        long seconds = Duration.between(clock.instant(), resetsAt).toSeconds();
        return Optional.of(Math.max(1, seconds + (seconds < 0 ? 0 : 1)));
    }
}
