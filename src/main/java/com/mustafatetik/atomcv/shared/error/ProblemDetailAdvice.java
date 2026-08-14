package com.mustafatetik.atomcv.shared.error;

import com.mustafatetik.atomcv.shared.security.CrossTenantAccessException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
     * An unknown path. Ordinary enough — a stale bookmark, a typo, a scanner —
     * that letting it reach the catch-all would answer 500 and fill the log
     * with stack traces for something that is not a failure at all.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handle(NoResourceFoundException exception) {
        return respond(UserFacingError.of(ErrorCode.RESOURCE_NOT_FOUND));
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

    private static ResponseEntity<ProblemDetail> respond(UserFacingError error) {
        return ResponseEntity.status(error.httpStatus()).body(ProblemDetails.from(error));
    }
}
