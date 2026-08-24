package com.mustafatetik.atomcv.jobs.api;

import com.mustafatetik.atomcv.jobs.api.dto.JobStatusResponse;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobRepository;
import com.mustafatetik.atomcv.jobs.sse.SseRegistry;
import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Following a queued piece of work (EK D.6.4).
 *
 * <p>Every read is scoped to the acting user. The job id is one of the two
 * identifiers this system hands to a browser, and a status endpoint that did
 * not check ownership would let anyone with a job id watch someone else's
 * generation — including its error, which names what their profile is missing
 * (absolute rule 3).
 *
 * <p>A job belonging to somebody else answers 404 rather than 403. Telling a
 * stranger that an id exists is itself information.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Following work that runs on a queue")
public class JobController {

    private final CurrentUser currentUser;
    private final JobRepository jobs;
    private final SseRegistry streams;

    JobController(CurrentUser currentUser, JobRepository jobs, SseRegistry streams) {
        this.currentUser = currentUser;
        this.jobs = jobs;
        this.streams = streams;
    }

    @Operation(
            summary = "Where a job has got to",
            description = """
                    `generationId` is present only when the status is \
                    `completed`, `error` only when it is `failed`. Both are \
                    terminal, and a client may stop polling on either.

                    Polling this is the supported fallback for a progress \
                    stream that closed without a terminal event — a spinner \
                    over work that already finished is the one outcome the \
                    product refuses to produce.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The job"),
            @ApiResponse(responseCode = "404",
                    description = "No such job, or it belongs to someone else",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{jobId}")
    public ResponseEntity<JobStatusResponse> status(@PathVariable UUID jobId) {
        Job job = jobs.findById(currentUser.require(), jobId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        return ResponseEntity.ok()
                // A job's state changes under the client by design; a cached
                // "queued" is what a stuck progress bar is made of.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(JobStatusResponse.of(job));
    }

    @Operation(
            summary = "Watch a job as it runs",
            description = """
                    A server-sent event stream. Three event names: `phase`                     while it runs, then exactly one of `completed` or                     `failed`, after which the stream closes.

                    The current state is sent immediately on connect, so a                     client that reconnects is caught up without replay — and a                     job that finished between the 202 and the subscribe sends                     its outcome rather than nothing at all.

                    `Last-Event-ID` is accepted and not replayed from: ids                     order the events of one stream, and the snapshot on                     connect does the catching up. If the stream ever closes                     without a terminal event, `GET /jobs/{jobId}` is the                     supported way to find out what happened.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The stream",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
            @ApiResponse(responseCode = "404",
                    description = "No such job, or it belongs to someone else",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(path = "/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID jobId) {
        // The ownership check comes first and it is the whole IDOR defense on
        // this endpoint (Bolum 30.6): a stream carries the job's error, which
        // names what a profile is missing.
        Job job = jobs.findById(currentUser.require(), jobId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        return streams.subscribe(job);
    }
}
