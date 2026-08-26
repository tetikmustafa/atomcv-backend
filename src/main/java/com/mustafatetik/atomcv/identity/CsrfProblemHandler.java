package com.mustafatetik.atomcv.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.ProblemDetails;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * A refused CSRF token leaves the API in the same shape as every other failure
 * (EK D.6.6, Bolum 35.4).
 *
 * <p>It needs its own handler because the rejection happens in a servlet
 * filter, below the dispatcher: {@code ProblemDetailAdvice} never sees it, and
 * without this the client would get Spring Security's bare 403 — an HTML-ish
 * body with no {@code code} field, which the frontend has no case for and
 * would render as an unknown error.
 */
@Component
public class CsrfProblemHandler implements AccessDeniedHandler {

    private final ObjectMapper json;

    CsrfProblemHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException denied) throws IOException {
        // Everything else is permitAll, so a non-CSRF denial here would be a
        // defect rather than a client mistake — and is reported as one.
        ErrorCode code = denied instanceof CsrfException
                ? ErrorCode.CSRF_TOKEN_INVALID
                : ErrorCode.INTERNAL_ERROR;
        UserFacingError error = UserFacingError.of(code);
        response.setStatus(error.httpStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ProblemDetails.from(error));
    }
}
