package com.mustafatetik.atomcv.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.shared.security.CrossTenantAccessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every failure leaves as the body of Bolum 35.4. The controller here exists
 * only to throw; the advice under test is production code.
 */
// `controllers` narrows the slice to this test's own controller, so the real
// ones stay out of it; `@Import` is what actually registers it, since a nested
// test class is never component-scanned.
@WebMvcTest(controllers = ProblemDetailAdviceTest.ThrowingController.class)
// No filter chain. Since Adim 3.3 put Spring Security on the classpath, a
// @WebMvcTest slice auto-configures its default chain — which is not ours and
// refuses everything — and each case below would assert Spring Security's 401
// instead of the advice under test. The chain has its own tests; this one is
// about the shape of a body.
@AutoConfigureMockMvc(addFilters = false)
// ClockConfig too: the advice reads a clock to compute Retry-After, and a
// @WebMvcTest slice carries no configuration that is not web-shaped.
@Import({ProblemDetailAdvice.class, ProblemDetailAdviceTest.ThrowingController.class,
        com.mustafatetik.atomcv.shared.config.ClockConfig.class})
class ProblemDetailAdviceTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void aNamedErrorLeavesAsTheDocumentedBody() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/errors/conflicting-preferences"))
                .andExpect(jsonPath("$.title").value("Conflicting preferences"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value("/test/conflict"))
                .andExpect(jsonPath("$.code").value("CONFLICTING_PREFERENCES"))
                .andExpect(jsonPath("$.params.pinnedPages").value(2.3))
                .andExpect(jsonPath("$.params.maxPages").value(1))
                .andExpect(jsonPath("$.resolutions[0].action").value("increase_page_limit"))
                .andExpect(jsonPath("$.resolutions[0].params.maxPages").value(3))
                .andExpect(jsonPath("$.resolutions[1].action").value("review_pins"))
                .andExpect(jsonPath("$.resolutions[1].params").doesNotExist());
    }

    @Test
    void anErrorWithoutParametersOmitsTheField() throws Exception {
        mvc.perform(get("/test/expired"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("GENERATION_ARTIFACT_EXPIRED"))
                .andExpect(jsonPath("$.params").doesNotExist())
                .andExpect(jsonPath("$.resolutions[0].action").value("retry"));
    }

    @Test
    void aCrossTenantWriteIsReportedAsADefectNotAsAPermission() throws Exception {
        // Reads return empty, so no legitimate client reaches this. Answering
        // 403 would also confirm the row exists.
        mvc.perform(get("/test/cross-tenant"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("profile"))));
    }

    @Test
    void aStaleWriteBecomesAVersionConflictThatCanBeRetried() throws Exception {
        mvc.perform(get("/test/stale"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.resolutions[0].action").value("retry"));
    }

    @Test
    void validationPublishesFieldNamesAndNotTheValuesThatFailed() throws Exception {
        mvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"headline\":\"far too long for the limit\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields").value(Matchers.contains("headline", "title")))
                // The rejected value is user content and must not come back out.
                .andExpect(content().string(Matchers.not(Matchers.containsString("far too long"))));
    }

    @Test
    void anUnexpectedFailureStillCarriesACode() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.title").value("Internal error"))
                // Nothing of the cause reaches the client.
                .andExpect(content().string(Matchers.not(Matchers.containsString("database"))));
    }

    @Test
    void anUnknownPathIsNotAServerFailure() throws Exception {
        mvc.perform(get("/test/there-is-no-such-thing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ── Protocol-level rejections. Every one of these answered 500 before,
    // and told the user the server had broken (EK D.6.8). ──────────────────

    @Test
    void aMediaTypeNothingConsumesIsTheClientsMistake() throws Exception {
        // The exact request Bolum 35.6 documented for the profile patches.
        mvc.perform(post("/test/validated")
                        .contentType(MediaType.valueOf("application/merge-patch+json"))
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void anAcceptHeaderNothingCanSatisfyIsAlsoTheClientsMistake() throws Exception {
        mvc.perform(get("/test/json-only").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    @Test
    void theWrongVerbAnswers405AndSaysWhichVerbsWork() throws Exception {
        mvc.perform(get("/test/validated"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                // RFC 9110 requires it, and it is what tells a client whether
                // it used the wrong verb or the wrong URL.
                .andExpect(header().string("Allow", Matchers.containsString("POST")));
    }

    @Test
    void aParameterThatWillNotConvertNamesTheParameterAndNotItsValue() throws Exception {
        mvc.perform(get("/test/json-only").param("id", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields").value(Matchers.contains("id")))
                // The value came from the user (absolute rule 4).
                .andExpect(content().string(Matchers.not(Matchers.containsString("not-a-uuid"))));
    }

    @Test
    void aMissingRequiredParameterIsNamedTheSameWay() throws Exception {
        mvc.perform(get("/test/needs-a-parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields").value(Matchers.contains("format")));
    }

    record ProfileForm(@NotBlank String title, @Size(max = 10) String headline) {
    }

    @RestController
    @Validated
    static class ThrowingController {

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ApiException(UserFacingError.with(ErrorCode.CONFLICTING_PREFERENCES)
                    .param("pinnedPages", 2.3)
                    .param("maxPages", 1)
                    .resolution(Resolution.of(ResolutionAction.INCREASE_PAGE_LIMIT, "maxPages", 3))
                    .resolution(ResolutionAction.REVIEW_PINS)
                    .build());
        }

        @GetMapping("/test/expired")
        void expired() {
            throw ApiException.of(ErrorCode.GENERATION_ARTIFACT_EXPIRED,
                    Resolution.of(ResolutionAction.RETRY));
        }

        @GetMapping("/test/cross-tenant")
        void crossTenant() {
            throw new CrossTenantAccessException("The row belongs to a different profile");
        }

        @GetMapping("/test/stale")
        void stale() {
            throw new ObjectOptimisticLockingFailureException("sections", null);
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("the database went away");
        }

        @PostMapping(path = "/test/validated", consumes = MediaType.APPLICATION_JSON_VALUE)
        void validated(@jakarta.validation.Valid @RequestBody ProfileForm form) {
        }

        @GetMapping(path = "/test/json-only", produces = MediaType.APPLICATION_JSON_VALUE)
        String jsonOnly(@RequestParam(required = false) UUID id) {
            return "{}";
        }

        @GetMapping("/test/needs-a-parameter")
        void needsAParameter(@RequestParam String format) {
        }
    }
}
