package com.mustafatetik.atomcv.generation.api;

import com.mustafatetik.atomcv.generation.api.dto.GeneralCvRequest;
import com.mustafatetik.atomcv.generation.pipeline.ErrorPresenter;
import com.mustafatetik.atomcv.generation.pipeline.GeneratedDocument;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.generation.service.CvGenerationService;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A CV out of the profile, with no job description involved (Bolum 19.4).
 *
 * <p><strong>Stage 1 only, and synchronous.</strong> Bolum 35.3's
 * {@code POST /generations} answers 202 with a job to follow, because a
 * generation with an LLM in it takes half a minute. General mode has no LLM
 * and no queue yet: this returns the document itself, and nothing is stored.
 * The queued contract arrives with the generation record in Stage 2
 * (EK D.8.8).
 */
@RestController
@RequestMapping("/api/v1/generations")
@Tag(name = "Generation", description = "Turning a profile into a document")
public class GenerationController {

    private final CurrentUser currentUser;
    private final CvGenerationService generations;
    private final ErrorPresenter errors;

    GenerationController(CurrentUser currentUser, CvGenerationService generations,
            ErrorPresenter errors) {

        this.currentUser = currentUser;
        this.generations = generations;
        this.errors = errors;
    }

    @Operation(
            summary = "Generate a general CV as a PDF",
            description = """
                    No job description, no LLM: the profile is scored on its own \
                    terms, selection fills the page, and the document comes back \
                    directly. Synchronous and stored nowhere — a generation \
                    resource with a job and a download link arrives in Stage 2.

                    The page limit is a guarantee. When the compiled document \
                    exceeds it the server shrinks the budget and tries again \
                    twice; only then does it answer PAGE_LIMIT_EXCEEDED, so \
                    retrying the same request unchanged will not help.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The document",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "422",
                    description = "INSUFFICIENT_PROFILE or PAGE_LIMIT_EXCEEDED",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONFLICTING_PREFERENCES",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "COMPILATION_FAILED",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(path = "/general", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generalCv(
            @Valid @RequestBody(required = false) GeneralCvRequest request) {

        GeneralCvRequest asked = request == null ? GeneralCvRequest.EMPTY : request;
        Result<GeneratedDocument> result = generations.generateGeneralCv(
                currentUser.require(), asked.maxPages(), asked.language());

        GeneratedDocument document = switch (result) {
            case Result.Ok<GeneratedDocument> ok -> ok.value();
            case Result.Err<GeneratedDocument> failed -> throw new ApiException(
                    errors.present(failed.error(), pageHeightPt()));
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename() + "\"")
                // No store: the document is built from personal data and is
                // cheap to make again.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(document.pdf());
    }

    /**
     * The filename carries a date and nothing else — a name in it would put
     * personal data into download folders and proxy logs (absolute rule 4).
     */
    private static String filename() {
        return "atomcv-cv-" + LocalDate.now() + ".pdf";
    }

    private static double pageHeightPt() {
        return TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC)
                .orElseThrow().pageTextHeightPt();
    }
}
