package com.mustafatetik.atomcv.ingestion.api;

import com.mustafatetik.atomcv.generation.api.dto.AcceptedJobResponse;
import com.mustafatetik.atomcv.billing.QuotaSubject;
import com.mustafatetik.atomcv.identity.ratelimit.ClientIp;
import com.mustafatetik.atomcv.ingestion.service.ProfileImportService;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobOwner;
import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Where a CV comes in (Bolum 31.1).
 *
 * <p><strong>Two answers, and the difference between them is the point.</strong>
 * A file this deployment cannot read is refused here and now, with a code that
 * says which of the five things went wrong (Bolum 31.2, Bolum 31.10). A file it
 * can read is answered {@code 202} with a job to follow, because what remains
 * is an LLM call over a whole document — Bolum 31.6 budgets eight seconds and
 * puts a screen in front of the person while it runs.
 *
 * <p>The bytes are not stored. They are read into text inside this request and
 * dropped; nothing about the upload reaches a disk.
 */
@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profile", description = "Building a profile from a CV")
public class ProfileImportController {

    private final ProfileImportService imports;
    private final CurrentUser currentUser;

    ProfileImportController(ProfileImportService imports, CurrentUser currentUser) {
        this.imports = imports;
        this.currentUser = currentUser;
    }

    @Operation(
            summary = "Build a profile from an uploaded CV",
            description = """
                    Accepts PDF, DOCX, TEX, TXT and MD, up to ten megabytes. \
                    The published list is `accepted` on a `415` — read it from \
                    there rather than hardcoding one, so a format added later \
                    reaches the file picker without a release.

                    Answers 202 with a job to follow. Everything that can be \
                    decided about the file itself is decided before that: an \
                    unreadable format, an oversized file, an encrypted PDF, a \
                    scan with no text in it, and a document that yielded \
                    nothing are all refused synchronously, because each of \
                    them is something the person acts on at once.

                    Send `Idempotency-Key`. An upload is the request a flaky \
                    connection repeats most easily, and profile extraction has \
                    the smallest daily allowance in the product.""")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Queued; follow the Location"),
            @ApiResponse(responseCode = "413", description = "Over ten megabytes",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Not a format we read",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "Encrypted, scanned, or holding nothing",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "The daily allowance is spent",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AcceptedJobResponse> importCv(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        JobOwner owner = JobOwner.of(currentUser);
        Job job = imports.importCv(owner, allowanceFor(owner, request),
                file.getOriginalFilename(), file.getContentType(), bytesOf(file),
                idempotencyKey);

        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/jobs/" + job.getId()))
                .body(AcceptedJobResponse.of(job));
    }

    /**
     * Whose daily ceiling this upload spends (Bolum 44.1).
     *
     * <p>An account spends its own; an anonymous caller spends their address's.
     * Not their session's, and Bolum 44.1 decides it: a session is a cookie,
     * and counting by one would hand an unlimited allowance to whoever clears
     * theirs.
     */
    private static QuotaSubject allowanceFor(JobOwner owner, HttpServletRequest request) {
        return owner.isAnonymous()
                ? QuotaSubject.ofAddress(ClientIp.of(request))
                : QuotaSubject.of(UserContext.of(owner.userId()));
    }

    /**
     * Read once, into memory.
     *
     * <p>Ten megabytes is the ceiling and the container has already refused
     * anything larger, so this is bounded by configuration rather than by
     * hope. A stream would be the alternative and would buy nothing: PDFBox
     * and POI both read a whole document before they answer.
     */
    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Could not read the uploaded file", unreadable);
        }
    }
}
