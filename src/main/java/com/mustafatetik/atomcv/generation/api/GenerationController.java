package com.mustafatetik.atomcv.generation.api;

import com.mustafatetik.atomcv.generation.api.dto.AcceptedJobResponse;
import com.mustafatetik.atomcv.generation.api.dto.CoverLetterRequest;
import com.mustafatetik.atomcv.generation.api.dto.CoverLetterResponse;
import com.mustafatetik.atomcv.generation.api.dto.FeedbackRequest;
import com.mustafatetik.atomcv.generation.api.dto.FeedbackResponse;
import com.mustafatetik.atomcv.generation.api.dto.GenerationPage;
import com.mustafatetik.atomcv.generation.api.dto.GenerationRequest;
import com.mustafatetik.atomcv.generation.api.dto.GenerationResponse;
import com.mustafatetik.atomcv.generation.api.dto.NaturalLanguageEditRequest;
import com.mustafatetik.atomcv.generation.api.dto.SelectionEditRequest;
import com.mustafatetik.atomcv.generation.api.dto.SelectionViewResponse;
import com.mustafatetik.atomcv.generation.pipeline.ErrorPresenter;
import com.mustafatetik.atomcv.generation.repository.GenerationCursor;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.selection.GenerationDirectives;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.coverletter.CoverLetterDraft;
import com.mustafatetik.atomcv.generation.service.CoverLetterRegenerationService;
import com.mustafatetik.atomcv.generation.service.FeedbackService;
import com.mustafatetik.atomcv.generation.service.GenerationDownloadService;
import com.mustafatetik.atomcv.generation.service.GenerationEnqueueService;
import com.mustafatetik.atomcv.generation.service.SelectionEditService;
import com.mustafatetik.atomcv.generation.service.SelectionViewService;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import com.mustafatetik.atomcv.shared.error.AccountFeature;
import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.Resolution;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import com.mustafatetik.atomcv.shared.ratelimit.RateLimitDecision;
import com.mustafatetik.atomcv.shared.ratelimit.RateLimiter;
import com.mustafatetik.atomcv.billing.QuotaSubject;
import com.mustafatetik.atomcv.generation.repository.AnonymousGenerations;
import com.mustafatetik.atomcv.identity.challenge.CallerChallenge;
import com.mustafatetik.atomcv.jobs.queue.JobOwner;
import com.mustafatetik.atomcv.profile.service.CallerProfiles;
import com.mustafatetik.atomcv.shared.ratelimit.ClientIp;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    private final CallerProfiles callers;
    private final CallerChallenge challenge;
    private final AnonymousGenerations anonymousRecords;
    private final GenerationEnqueueService enqueue;
    private final GenerationDownloadService downloads;
    private final GenerationRepository generations;
    private final CoverLetterRegenerationService coverLetters;
    private final RateLimiter rateLimiter;
    private final SelectionEditService edits;
    private final SelectionViewService selectionView;
    private final FeedbackService feedback;
    private final Clock clock;
    private final ErrorPresenter errors;

    /**
     * Bolum 34.6 wants a person to be able to try a few drafts, and an LLM
     * endpoint with no ceiling at all is a bill somebody else writes. Ten an
     * hour is several tries per generation and no loop.
     */
    private static final int LETTERS_PER_HOUR = 10;

    /**
     * A history screen shows a screenful; twenty is that, and the cursor is
     * there for anyone who wants more. A default of "everything" would make
     * the first request of a heavy account the slowest one it ever makes.
     *
     * <p>A String because it is an annotation default, parsed back where it is
     * used -- one number, not two that can drift.
     */
    private static final String DEFAULT_PAGE_SIZE = "20";

    /** The ceiling a caller may ask for. Beyond it the request is clamped, not refused. */
    private static final int MAX_PAGE_SIZE = 100;

    /** What a .docx is, spelled out because MediaType has no constant for it. */
    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    /**
     * Published on every 429 here, because a header nobody documented is a
     * header nobody reads (F-021).
     *
     * <p>{@code ProblemDetailAdvice} has always derived it from the same
     * {@code resetsAt} the body carries, so this describes what was already
     * being sent rather than adding anything — but the frontend built the
     * vaguer "try again shortly" sentence off the schema, and the schema was
     * the only place that did not say so.
     */
    private static final String RETRY_AFTER_DESCRIPTION =
            "Seconds to wait, rounded up and never zero. The same moment as "
                    + "`params.resetsAt`, as a duration: it is the one of the two "
                    + "that is still right when the client's own clock is wrong.";

    GenerationController(CurrentUser currentUser, CallerProfiles callers,
            CallerChallenge challenge,
            AnonymousGenerations anonymousRecords,
            GenerationEnqueueService enqueue, GenerationDownloadService downloads,
            GenerationRepository generations, CoverLetterRegenerationService coverLetters,
            SelectionEditService edits, SelectionViewService selectionView,
            RateLimiter rateLimiter, FeedbackService feedback, Clock clock,
            ErrorPresenter errors) {

        this.currentUser = currentUser;
        this.callers = callers;
        this.challenge = challenge;
        this.anonymousRecords = anonymousRecords;
        this.enqueue = enqueue;
        this.downloads = downloads;
        this.generations = generations;
        this.coverLetters = coverLetters;
        this.edits = edits;
        this.selectionView = selectionView;
        this.rateLimiter = rateLimiter;
        this.feedback = feedback;
        this.clock = clock;
        this.errors = errors;
    }

    @Operation(
            operationId = "generate",
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
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429",
                    description = "QUOTA_EXCEEDED — the day's generations are spent",
                    headers = @Header(name = HttpHeaders.RETRY_AFTER,
                            description = RETRY_AFTER_DESCRIPTION,
                            schema = @Schema(type = "integer")),
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<AcceptedJobResponse> generate(
            @Valid @RequestBody GenerationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            jakarta.servlet.http.HttpServletRequest http) {

        // Bolum 44.4, and first: an anonymous request that cannot show a person
        // behind it must not reach the quota, let alone a model.
        challenge.requireOfAnonymous(request.challengeToken());

        JobOwner owner = JobOwner.of(currentUser);
        Result<Job> queued = enqueue.enqueue(
                owner, allowanceFor(owner, http), callers.owned(),
                request.jobDescription(), request.acknowledged(),
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
            operationId = "listGenerations",
            summary = "The generations this account has made, newest first",
            description = """
                    `capabilities.canSaveHistory` says these are kept; this is                     where they are read (F-020).

                    Cursor pagination, not offset: the list grows from the top,                     and a page two taken after a new generation lands would                     repeat one row and hide another. Pass the `nextCursor` of                     a page back as `cursor` to get the one after it; its                     absence is the end of the history.

                    `total` counts the whole history rather than the page.                     The one screen that needs it cannot page — deleting an                     account has to say what goes, and a number that meant "at                     least this many" would be worse there than none.

                    Generations a hand edit replaced are **not listed** and not                     counted. Faz G writes a new generation per edit and retires                     the one before it, so twenty edits of one CV would otherwise                     be twenty-one rows and one of them the CV. Nothing is                     deleted: a retired row is still there and still downloadable                     by id, and deleting the account still takes it.

                    A row carries no posting and no letter, only whether                     there is a letter to open. The posting stays on the row                     (absolute rule 4).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of history"),
            @ApiResponse(responseCode = "400",
                    description = "VALIDATION_FAILED — the cursor was not one of ours",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenerationPage> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = DEFAULT_PAGE_SIZE) int limit) {

        // Scoped, and here it is the whole of the IDOR defence: this is the
        // one endpoint in the file that names no id, so nothing but the acting
        // user stands between a caller and everybody's history (rule 3).
        UserContext user = currentUser.require();

        GenerationCursor from;
        try {
            from = GenerationCursor.decode(cursor).orElse(null);
        } catch (IllegalArgumentException malformed) {
            // A client only ever echoes a cursor back, so a broken one is its
            // mistake. Left alone this reaches the catch-all and answers 500.
            throw new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                    .param("fields", java.util.List.of("cursor"))
                    .build());
        }

        var page = generations.findPage(user, from, clamped(limit));

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(GenerationPage.of(page, generations.countFor(user)));
    }

    /**
     * A page size the caller asked for, within what the server will serve.
     *
     * <p>Clamped rather than refused: a limit is a preference, and answering
     * 400 to {@code limit=1000} would make a client handle an error where the
     * useful behaviour is obvious. Zero and negatives fall to the default,
     * because a page of no rows is a request that cannot be walked.
     */
    private static int clamped(int limit) {
        return limit < 1 ? Integer.parseInt(DEFAULT_PAGE_SIZE) : Math.min(limit, MAX_PAGE_SIZE);
    }

    @Operation(
            operationId = "readGeneration",
            summary = "One generation and how well it fits the posting",
            description = """
                    Carries Faz F's coverage report: how many of the posting's                     required and preferred skills the finished page actually                     says, which ones are missing, and a level over the counts.

                    **Counts, never a percentage.** Bolum 23.3 forbids one by                     name — the measurement compares skill names, and a figure                     to the decimal place invites the reader to treat it as a                     hiring probability.

                    The report is measured on the atoms that reached the page,                     not on everything that was ranked, so it never credits a                     skill the document does not claim. A general-mode                     generation has no report at all: there was no posting to                     be relevant to.

                    Carries `feedback` when this person has judged it, so a                     reload shows the thumb they pressed rather than asking                     again, and so Bolum 48.4's 48-hour grant stays visible                     the day after it was given. Absent when they have not                     judged it; the comment never travels.""")
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
        Generation generation = forCaller(generationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        // F-019: the verdict and its grant ride along, so a reload shows the
        // thumb that was pressed instead of asking for it again -- and so the
        // person can still see, the day after granting it, whether it is open,
        // when it runs out and whether anybody has read it (Bolum 48.4) — the
        // last of those since the offline reader stamps it (B-078).
        //
        // Absent for an anonymous session, and absent rather than refused: a
        // verdict is a row keyed by user and a support grant is consent an
        // account gives, so there is nothing to read and nothing to say. Asking
        // for a user here is what made the whole endpoint answer 401 to a caller
        // whose generation it had already found.
        FeedbackResponse verdict = currentUser.find()
                .flatMap(user -> feedback.read(user, generationId))
                .map(recorded -> FeedbackResponse.of(generationId, recorded.verdict(),
                        recorded.grant(), clock.instant()))
                .orElse(null);

        // F-031: a retired generation can say where its replacement is. The
        // edge is stored the other way round -- the new row names the one it
        // replaced -- and the history list leaves retired rows out, so a screen
        // holding this id had no way to find the newer one. Looked up only when
        // there is something to find, which is a small minority of reads.
        UUID supersededBy = edits.isStale(generation)
                ? successorOf(generationId).map(Generation::getId).orElse(null)
                : null;

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(GenerationResponse.of(generation, verdict, supersededBy));
    }

    @Operation(
            summary = "What this generation weighed, and what reached the page",
            operationId = "readSelection",
            description = """
                    Bolum 24.4's toggle, as a list a screen can draw (F-031).

                    Every atom this generation ranked is here, the ones that
                    reached the page first and the ones that did not after
                    them, each with the text it competed as and an `onPage`
                    flag. **The ids are exactly the ids the edit endpoint
                    accepts** -- which is the reason this exists: an edit
                    refuses an atom this generation never weighed, so controls
                    drawn from today's profile would include buttons that
                    answer 400.

                    The text is what *this* CV said, not what the profile says
                    today: the wording Faz D wrote where there was one, and the
                    variant the selection named otherwise. Editing a bullet
                    afterwards does not rewrite the list of a CV already made.
                    A held-back line carries the profile's wording, because
                    this generation never printed one for it -- putting it back
                    runs Faz D over it and may word it differently.

                    Not capped. The sentence endpoint shows a model thirty
                    held-back lines because a prompt costs money; a person
                    scrolling their own history is not paying by the line.

                    An atom deleted from the profile since is absent rather
                    than listed: it cannot be put back, and asking to drop it
                    is already true.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The lines, page first"),
            @ApiResponse(responseCode = "404",
                    description = "No such generation, or it belongs to someone else",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(path = "/{generationId}/selection", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SelectionViewResponse> readSelection(@PathVariable UUID generationId) {
        // Scoped, and it is the whole IDOR defence here: the lines are the
        // person's own writing, so somebody else's id answers 404 (rule 3).
        Generation generation = forCaller(generationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(SelectionViewResponse.of(
                        generationId, selectionView.linesOf(generation)));
    }

    @Operation(
            operationId = "downloadGeneration",
            summary = "Download a generation, as a PDF or a Word document",
            description = """
                    Re-rendered from the stored content snapshot, never from                     the profile. Editing a bullet afterwards does not change                     a CV that has already been sent — the document that comes                     back is the one that was made.

                    No LLM and no scoring: one compilation, and the same                     generation produces the same bytes on any day.

                    `format=docx` writes the same content as a Word                     document. **The page limit is approximate there** (Bolum                     22.6): the atoms are the ones that fitted a typeset page,                     and Word sets them in whatever room its own fonts take.                     Same CV, not a second promise -- say so next to the                     button.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The document",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "400",
                    description = "VALIDATION_FAILED — a format that is not "
                            + "`pdf` or `docx`",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No such generation, or it belongs to someone else",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "410",
                    description = "GENERATION_ARTIFACT_EXPIRED — nothing left to re-render",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(path = "/{generationId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID generationId,
            @RequestParam(required = false, defaultValue = "pdf") String format) {
        Generation generation = forCaller(generationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        if (generation.getContentSnapshot() == null) {
            // The selection is still there, so "make it again" is the honest
            // answer — rendering today's profile would hand back a document
            // that was never sent to anyone (EK D.6.3).
            throw ApiException.of(ErrorCode.GENERATION_ARTIFACT_EXPIRED,
                    new Resolution(ResolutionAction.RETRY, null));
        }

        if ("docx".equalsIgnoreCase(format)) {
            // No compilation and so no failure to present: POI writes the
            // package itself. The page guarantee does not travel with it
            // either (Bolum 22.6) -- the atoms are the ones that fit a LaTeX
            // page, and Word may set them in a little more or less room.
            return attachment(downloads.renderDocx(generation), DOCX_MEDIA_TYPE, "docx");
        }
        if (!"pdf".equalsIgnoreCase(format)) {
            // Bolum 35.3's map offers `source` too, and nothing serves it yet.
            // Named rather than ignored: a client asking for one and silently
            // getting a PDF would ship a .tex button that downloads a PDF.
            throw new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                    .param("fields", List.of("format"))
                    .build());
        }

        Result<byte[]> pdf = downloads.render(generation);
        byte[] bytes = switch (pdf) {
            case Result.Ok<byte[]> ok -> ok.value();
            case Result.Err<byte[]> failed -> throw new ApiException(
                    errors.present(failed.error(), pageHeightPt()));
        };

        return attachment(bytes, MediaType.APPLICATION_PDF, "pdf");
    }

    /**
     * One body, one filename rule (absolute rule 4: no name in it).
     */
    private static ResponseEntity<byte[]> attachment(
            byte[] bytes, MediaType type, String extension) {

        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename(extension) + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }

    @Operation(
            operationId = "editSelection",
            summary = "Keep or drop atoms by hand, and re-make the CV",
            description = """
                    Bolum 24.4. An edit applies to the **selection state**, \
                    never to the rendered document — which is what keeps the \
                    page limit true after twenty of them: every edit goes \
                    back through the selection that made the promise.

                    Answers 202 with a job, like a generation, because it \
                    re-runs the renderer and a real compiler. It does not \
                    re-run Faz A or Faz B — the posting was read once and the \
                    profile ranked against it once, and a toggle changes \
                    neither answer — and Faz D carries the wording it already \
                    wrote. **No model call, and nothing off the day's \
                    allowance.**

                    The job's terminal event names a **new** generation. The \
                    edited one stays, marked superseded, and its id comes \
                    back as `supersededGenerationId`.

                    An atom this generation never weighed is refused rather \
                    than ignored, because ignoring it would answer 202 and \
                    hand back the same document.""")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Queued; follow the Location"),
            @ApiResponse(responseCode = "400",
                    description = "VALIDATION_FAILED — an empty edit, an atom named in "
                            + "both lists, or one this generation never weighed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No such generation, or it belongs to someone else",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "GENERATION_SUPERSEDED — a newer generation has "
                            + "replaced this one; edit that",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(path = "/{generationId}/selection", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AcceptedJobResponse> editSelection(
            @PathVariable UUID generationId,
            @Valid @RequestBody SelectionEditRequest request) {

        // Scoped, and it is the IDOR defence here: an edit of somebody else's
        // generation answers 404 (absolute rule 3).
        Generation parent = forCaller(generationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        if (request.isEmpty()) {
            // Queuing it would spend a compilation to produce the document the
            // caller is already looking at.
            throw new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                    .param("fields", List.of("include", "exclude"))
                    .build());
        }

        GenerationDirectives asked;
        try {
            asked = new GenerationDirectives(request.include(), request.exclude());
        } catch (IllegalArgumentException both) {
            // One atom in both lists. The record refuses it rather than
            // picking, because whichever we picked would be wrong half the
            // time -- and only a client can send it.
            throw new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                    .param("fields", List.of("include", "exclude"))
                    .build());
        }

        if (edits.isStale(parent)) {
            throw ApiException.of(ErrorCode.GENERATION_SUPERSEDED);
        }

        List<String> unknown = edits.unknownIn(parent, asked);
        if (!unknown.isEmpty()) {
            throw new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                    .param("fields", unknown)
                    .build());
        }

        Job job = edits.enqueue(JobOwner.of(currentUser), parent, asked);

        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/jobs/" + job.getId()))
                .body(AcceptedJobResponse.of(job));
    }

    @Operation(
            operationId = "editBySentence",
            summary = "Say what should change, in your own words",
            description = """
                    Bolum 24.2, and the other half of the toggle next door. \
                    One sentence is read into a change of **which atoms are on \
                    the page**, and the CV is re-made from its own selection \
                    state — so the page limit is re-checked and still holds, \
                    however many sentences it takes.

                    202 with a job, and the model is asked exactly once: it \
                    sees the lines **numbered**, never their ids, and answers \
                    with numbers. It cannot name a bullet that does not exist.

                    **This one costs a generation** off the day's allowance, \
                    unlike the hand toggle. Refunded when the sentence named \
                    no line.

                    What it does *not* do: reword a line, change the tone, or \
                    resize the page. A sentence asking for any of those is \
                    answered `EDIT_NOT_UNDERSTOOD` rather than guessed at — \
                    removing the wrong bullet is worse than saying nothing, \
                    because the person may not notice.""")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Queued; follow the Location"),
            @ApiResponse(responseCode = "400",
                    description = "VALIDATION_FAILED — an empty or over-long sentence",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No such generation, or it belongs to someone else",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "GENERATION_SUPERSEDED — a newer generation has "
                            + "replaced this one; edit that",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429",
                    description = "QUOTA_EXCEEDED — the day's generations are spent",
                    headers = @Header(name = HttpHeaders.RETRY_AFTER,
                            description = RETRY_AFTER_DESCRIPTION,
                            schema = @Schema(type = "integer")),
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(path = "/{generationId}/edits", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AcceptedJobResponse> edit(
            @PathVariable UUID generationId,
            @Valid @RequestBody NaturalLanguageEditRequest request,
            jakarta.servlet.http.HttpServletRequest http) {

        // Scoped, and it is the IDOR defence here too (absolute rule 3).
        Generation parent = forCaller(generationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        if (edits.isStale(parent)) {
            throw ApiException.of(ErrorCode.GENERATION_SUPERSEDED);
        }

        JobOwner owner = JobOwner.of(currentUser);
        Result<Job> queued = edits.enqueue(
                owner, allowanceFor(owner, http), parent, request.instruction());

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
            operationId = "regenerateCoverLetter",
            summary = "Write a covering letter for a generation, or another one",
            description = """
                    Bolum 34. The letter is written from the atoms that                     reached the page, which is what makes it consistent with                     the CV that was sent — not from today's profile, and not                     from anything the model knows about the company.

                    Off the main generation path on purpose: it is a second                     LLM call and most people want a CV. Ask for it here, or                     set `coverLetter: true` when generating.

                    Three variants (`default`, `shorter`, `more_formal`),                     and each press replaces the stored letter — trying                     another draft leaves one letter, not three.

                    **It can refuse.** A letter has no original to fall back                     on, so a draft that claims a skill the page does not carry,                     overstates the experience, or greets the wrong company is                     thrown away twice and then reported as                     `COVER_LETTER_REJECTED`. Another press is a different                     draft.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The letter"),
            @ApiResponse(responseCode = "403",
                    description = "FEATURE_REQUIRES_ACCOUNT — `params.feature` is "
                            + "`cover_letter`, and the resolution is `sign_up`. An "
                            + "anonymous session may hold this generation and "
                            + "still not have this control",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
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
                    headers = @Header(name = HttpHeaders.RETRY_AFTER,
                            description = RETRY_AFTER_DESCRIPTION,
                            schema = @Schema(type = "integer")),
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

        // Ahead of the lookup: a letter is what § 35.7 gives an account, and
        // an anonymous session asking for one is a closed control, not a
        // generation it cannot find (F-030).
        UserContext user = accountFor(AccountFeature.COVER_LETTER);

        // Scoped, and it is the IDOR defense on this endpoint too: someone
        // else's generation answers 404, because that an id exists is itself
        // information (absolute rule 3).
        Generation generation = coverLetters.find(user, generationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        RateLimitDecision allowed = rateLimiter.check("cover_letter",
                user.userId().toString(),
                LETTERS_PER_HOUR, Duration.ofHours(1));
        if (!allowed.allowed()) {
            throw new ApiException(UserFacingError.with(ErrorCode.RATE_LIMITED)
                    .param("resetsAt", allowed.resetsAt())
                    .build());
        }

        Result<CoverLetterDraft> written = coverLetters.rewrite(
                user, generation, asked.styleOrDefault(),
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

    @Operation(
            operationId = "recordFeedback",
            summary = "Say what you thought of a generation",
            description = """
                    A thumb, and everything after it is optional. One verdict                     per person per generation: pressing the other one changes                     your mind rather than adding a second opinion.

                    `contentGranted` is Bolum 48.4's consent. Ticking it lets                     the CV's own content be read for forty-eight hours to work                     out what went wrong — everything else in this product is                     diagnosed from shapes and counts, and this is the one door                     through that. The response echoes the grant back,                     how long it has left, and whether anybody has read it —                     `accessedAt` is null until the offline support reader                     stamps it, which is the only thing that can (B-078).                     Sending `contentGranted: false` later                     withdraws a grant that is still open.

                    The comment is stored and never logged. It is not sent                     back either: you wrote it, you have it.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recorded"),
            @ApiResponse(responseCode = "400",
                    description = "VALIDATION_FAILED — rating is 1 or -1",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "FEATURE_REQUIRES_ACCOUNT — `params.feature` is "
                            + "`feedback`, and the resolution is `sign_up`. An "
                            + "anonymous session may hold this generation and "
                            + "still not have this control",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No such generation, or it belongs to someone else",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(path = "/{generationId}/feedback", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FeedbackResponse> feedback(
            @PathVariable UUID generationId,
            @Valid @RequestBody FeedbackRequest request) {

        // A verdict is a row keyed to a user and the support grant is an
        // account's consent, so there is nothing an anonymous session can
        // record. Ahead of the lookup for the same reason as above (F-030).
        UserContext user = accountFor(AccountFeature.FEEDBACK);

        // Scoped, and it is the IDOR defense here too: a verdict on somebody
        // else's generation answers 404 (absolute rule 3).
        feedback.find(user, generationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        var recorded = feedback.record(user, generationId,
                request.ratingValue(), request.domainCategory(), request.comment(),
                request.granted());

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(FeedbackResponse.of(generationId, recorded.verdict(),
                        recorded.grant(), clock.instant()));
    }

    /**
     * The caller, when this endpoint is one § 35.7 gives an account and not a
     * session (F-030).
     *
     * <p><strong>Not {@code currentUser.require()}, and the difference is what
     * the user reads.</strong> Both of these endpoints called it, so an
     * anonymous session pressing a thumb or asking for a letter was answered
     * {@code 401 AUTHENTICATION_REQUIRED} — which says a session was needed and
     * none arrived, to somebody holding a perfectly good one. The screen writes
     * "your session ended" from that, and the diagnosis is wrong.
     * {@code FEATURE_REQUIRES_ACCOUNT} is the code {@code ErrorCode}'s own note
     * reserves for a feature an anonymous caller cannot reach, it carries
     * {@code sign_up}, and it names which control was pressed.
     *
     * <p>A request carrying <em>nothing</em> still gets the 401: that is the
     * plain case the other code is for, and claiming a session exists when
     * none does would be the same wrong sentence in the other direction.
     */
    private UserContext accountFor(AccountFeature feature) {
        Optional<UserContext> account = currentUser.find();
        if (account.isPresent()) {
            return account.get();
        }
        if (currentUser.anonymousSession().isEmpty()) {
            throw ApiException.of(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        throw new ApiException(UserFacingError.with(ErrorCode.FEATURE_REQUIRES_ACCOUNT)
                .param("feature", feature.wireValue())
                .resolution(ResolutionAction.SIGN_UP)
                .build());
    }

    /**
     * The filename carries a date and nothing else — a name in it would put
     * personal data into download folders and proxy logs (absolute rule 4).
     */
    private static String filename(String extension) {
        return "atomcv-cv-" + LocalDate.now() + "." + extension;
    }

    private static double pageHeightPt() {
        return TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC)
                .orElseThrow().pageTextHeightPt();
    }
    /**
     * Whose ceiling this generation takes (Bolum 44.1).
     *
     * <p>An account pays by its own id; a caller with no account pays by
     * address, because a session is a cookie and counting by one would give an
     * unlimited allowance to whoever clears theirs. The same shape
     * {@code ProfileImportController} uses, and the value travels into the
     * payload so the worker can give it back.
     */
    private static QuotaSubject allowanceFor(
            JobOwner owner, jakarta.servlet.http.HttpServletRequest http) {

        return owner.isAnonymous()
                ? QuotaSubject.ofAddress(ClientIp.of(http))
                : QuotaSubject.of(UserContext.of(owner.userId()));
    }

    /**
     * One generation belonging to whoever is calling (Bolum 9, absolute rule 3).
     *
     * <p><strong>Two doors and no third.</strong> An account's generations are
     * user-scoped, and a row with no owner reads as absent there — correctly. An
     * anonymous session's are profile-scoped, because a generation already
     * carries the {@code profile_id} the session owns. Which door is taken is
     * decided by whether there is an account, never by trying both: handing a
     * persistent ref to the anonymous reader is a programming error and it
     * refuses rather than falling through.
     *
     * <p>Absent for somebody else's id, and 404 rather than 403 at the call
     * site — that an id exists is itself information. The generation id reaches
     * a browser twice, in the job's terminal event and in the download link, so
     * this is the third place it can be spent and the whole defence on both
     * endpoints.
     *
     * <p>{@code successorOf} below takes the same two doors for the same
     * reason (F-031): the edge out of a retired generation is followed under
     * the caller's own scope, so the id it answers with can only ever be one
     * the caller could have read anyway.
     */
    private Optional<Generation> successorOf(UUID generationId) {
        Optional<UserContext> account = currentUser.find();
        return account.isPresent()
                ? generations.successorOf(account.get(), generationId)
                : anonymousRecords.successorOf(callers.ref(), generationId);
    }

    private Optional<Generation> forCaller(UUID generationId) {
        Optional<UserContext> account = currentUser.find();
        return account.isPresent()
                ? generations.findById(account.get(), generationId)
                : anonymousRecords.findById(callers.ref(), generationId);
    }

}
