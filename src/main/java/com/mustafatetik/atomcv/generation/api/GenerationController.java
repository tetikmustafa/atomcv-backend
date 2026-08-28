package com.mustafatetik.atomcv.generation.api;

import com.mustafatetik.atomcv.generation.api.dto.AcceptedJobResponse;
import com.mustafatetik.atomcv.generation.api.dto.CoverLetterRequest;
import com.mustafatetik.atomcv.generation.api.dto.CoverLetterResponse;
import com.mustafatetik.atomcv.generation.api.dto.GenerationRequest;
import com.mustafatetik.atomcv.generation.api.dto.GenerationResponse;
import com.mustafatetik.atomcv.generation.pipeline.ErrorPresenter;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.coverletter.CoverLetterDraft;
import com.mustafatetik.atomcv.generation.service.CoverLetterRegenerationService;
import com.mustafatetik.atomcv.generation.service.GenerationDownloadService;
import com.mustafatetik.atomcv.generation.service.GenerationEnqueueService;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.Resolution;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import com.mustafatetik.atomcv.identity.ratelimit.RateLimitDecision;
import com.mustafatetik.atomcv.identity.ratelimit.RateLimiter;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asking for a CV, and getting one back (Bolum 35.3, Bolum 19.4).
 *
 * <p>One way in for both modes. A request with a posting is scored against it;
 * a request without one is a general CV, which skips Faz A and Faz B and is
 * otherwise the same pipeline. Stage 1's synchronous
 * {@code POST /generations/general} is gone — it existed because there was no
 * queue and no generation record, and both now exist (EK D.8.8, D.9 · 22).
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
    private final GenerationEnqueueService enqueue;
    private final GenerationDownloadService downloads;
    private final GenerationRepository generations;
    private final CoverLetterRegenerationService coverLetters;
    private final RateLimiter rateLimiter;
    private final ErrorPresenter errors;

    /**
     * Bolum 34.6 wants a person to be able to try a few drafts, and an LLM
     * endpoint with no ceiling at all is a bill somebody else writes. Ten an
     * hour is several tries per generation and no loop.
     */
    private static final int LETTERS_PER_HOUR = 10;

    GenerationController(CurrentUser currentUser,
            GenerationEnqueueService enqueue, GenerationDownloadService downloads,
            GenerationRepository generations, CoverLetterRegenerationService coverLetters,
            RateLimiter rateLimiter, ErrorPresenter errors) {

        this.currentUser = currentUser;
        this.enqueue = enqueue;
        this.downloads = downloads;
        this.generations = generations;
        this.coverLetters = coverLetters;
        this.rateLimiter = rateLimiter;
        this.errors = errors;
    }

    @Operation(
            summary = "Generate a CV against a job posting",
            description = """
                    Answers 202 with a job to follow. A generation reads the                     posting with an LLM, scores the whole profile against it,                     then renders and compiles — half a minute is ordinary, and                     a request held open for that long is a request that times                     out somewhere in between.

                    The preflights are synchronous. A posting that does not                     read as one and a profile with nothing in it are both                     refused here, on the spot, rather than accepted and failed                     thirty seconds later.

                    `Idempotency-Key` is honoured: the same key from the same                     user answers with the job it already made, so a double                     click produces one CV and not two.""")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Queued; follow the Location"),
            @ApiResponse(responseCode = "422",
                    description = "UNPARSEABLE_JOB_DESCRIPTION or INSUFFICIENT_PROFILE",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<AcceptedJobResponse> generate(
            @Valid @RequestBody GenerationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        Result<Job> queued = enqueue.enqueue(
                currentUser.require(), request.jobDescription(), request.acknowledged(),
                request.maxPages(), request.language(), request.wantsCoverLetter(),
                idempotencyKey);

        Job job = switch (queued) {
            case Result.Ok<Job> ok -> ok.value();
            case Result.Err<Job> refused -> throw new ApiException(
                    errors.present(refused.error(), pageHeightPt()));
        };

        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/jobs/" + job.getId()))
                .body(AcceptedJobResponse.of(job));
    }

    @Operation(
            summary = "One generation and how well it fits the posting",
            description = """
                    Carries Faz F's coverage report: how many of the posting's                     required and preferred skills the finished page actually                     says, which ones are missing, and a level over the counts.

                    **Counts, never a percentage.** Bolum 23.3 forbids one by                     name — the measurement compares skill names, and a figure                     to the decimal place invites the reader to treat it as a                     hiring probability.

                    The report is measured on the atoms that reached the page,                     not on everything that was ranked, so it never credits a                     skill the document does not claim. A general-mode                     generation has no report at all: there was no posting to                     be relevant to.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The generation"),
            @ApiResponse(responseCode = "404",
                    description = "No such generation, or it belongs to someone else",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(path = "/{generationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenerationResponse> read(@PathVariable UUID generationId) {
        // Scoped, and it is the whole IDOR defense on this endpoint: the
        // generation id reaches a browser twice and this is the third place it
        // can be spent (absolute rule 3). Someone else's id answers 404 rather
        // than 403 — that an id exists is itself information.
        Generation generation = generations.findById(currentUser.require(), generationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(GenerationResponse.of(generation));
    }

    @Operation(
            summary = "Download a generation as a PDF",
            description = """
                    Re-rendered from the stored content snapshot, never from                     the profile. Editing a bullet afterwards does not change                     a CV that has already been sent — the document that comes                     back is the one that was made.

                    No LLM and no scoring: one compilation, and the same                     generation produces the same bytes on any day.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The document",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "404",
                    description = "No such generation, or it belongs to someone else",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "410",
                    description = "GENERATION_ARTIFACT_EXPIRED — nothing left to re-render",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(path = "/{generationId}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable UUID generationId) {
        Generation generation = downloads.find(currentUser.require(), generationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        if (generation.getContentSnapshot() == null) {
            // The selection is still there, so "make it again" is the honest
            // answer — rendering today's profile would hand back a document
            // that was never sent to anyone (EK D.6.3).
            throw ApiException.of(ErrorCode.GENERATION_ARTIFACT_EXPIRED,
                    new Resolution(ResolutionAction.RETRY, null));
        }

        Result<byte[]> pdf = downloads.render(generation);
        byte[] bytes = switch (pdf) {
            case Result.Ok<byte[]> ok -> ok.value();
            case Result.Err<byte[]> failed -> throw new ApiException(
                    errors.present(failed.error(), pageHeightPt()));
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }

    @Operation(
            summary = "Write a covering letter for a generation, or another one",
            description = """
                    Bolum 34. The letter is written from the atoms that                     reached the page, which is what makes it consistent with                     the CV that was sent — not from today's profile, and not                     from anything the model knows about the company.

                    Off the main generation path on purpose: it is a second                     LLM call and most people want a CV. Ask for it here, or                     set `coverLetter: true` when generating.

                    Three variants (`default`, `shorter`, `more_formal`),                     and each press replaces the stored letter — trying                     another draft leaves one letter, not three.

                    **It can refuse.** A letter has no original to fall back                     on, so a draft that claims a skill the page does not carry,                     overstates the experience, or greets the wrong company is                     thrown away twice and then reported as                     `COVER_LETTER_REJECTED`. Another press is a different                     draft.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The letter"),
            @ApiResponse(responseCode = "404",
                    description = "No such generation, or it belongs to someone else",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "COVER_LETTER_REJECTED — nothing honest could be written",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429",
                    description = "RATE_LIMITED — ten letters an hour",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(path = "/{generationId}/cover-letter/regenerate",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CoverLetterResponse> coverLetter(
            @PathVariable UUID generationId,
            @Valid @RequestBody(required = false) CoverLetterRequest request) {

        CoverLetterRequest asked = request == null
                ? new CoverLetterRequest(null, null)
                : request;

        // Scoped, and it is the IDOR defense on this endpoint too: someone
        // else's generation answers 404, because that an id exists is itself
        // information (absolute rule 3).
        Generation generation = coverLetters.find(currentUser.require(), generationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        RateLimitDecision allowed = rateLimiter.check("cover_letter",
                currentUser.require().userId().toString(),
                LETTERS_PER_HOUR, Duration.ofHours(1));
        if (!allowed.allowed()) {
            throw new ApiException(UserFacingError.with(ErrorCode.RATE_LIMITED)
                    .param("resetsAt", allowed.resetsAt())
                    .build());
        }

        Result<CoverLetterDraft> written = coverLetters.rewrite(
                currentUser.require(), generation, asked.styleOrDefault(),
                asked.companyNoteOrBlank());

        CoverLetterDraft draft = switch (written) {
            case Result.Ok<CoverLetterDraft> ok -> ok.value();
            case Result.Err<CoverLetterDraft> refused -> throw new ApiException(
                    errors.present(refused.error(), pageHeightPt()));
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new CoverLetterResponse(
                        generationId, draft.plainText(), asked.styleOrDefault()));
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
