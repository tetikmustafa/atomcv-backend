package com.mustafatetik.atomcv.generation.api.dto;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * What a 202 carries (Bolum 35.3).
 *
 * <p>{@code streamUrl} is published rather than left to the client to build:
 * it is the one place the shape of that path is decided, and a client that
 * assembled it itself would break silently the day it moved. Polling
 * {@code GET /jobs/{id}} stays supported — EK D.6.4 names it the fallback for
 * a stream that closed without a terminal event.
 */
@Schema(description = "A generation that was accepted and queued")
public record AcceptedJobResponse(

        @Schema(description = "Follow it at /api/v1/jobs/{jobId}")
        UUID jobId,

        JobStatus status,

        @Schema(description = "Server-sent events for this job",
                example = "/api/v1/jobs/9b1c4e7a-.../stream")
        String streamUrl) {

    public static AcceptedJobResponse of(Job job) {
        return new AcceptedJobResponse(job.getId(), job.getStatus(),
                "/api/v1/jobs/" + job.getId() + "/stream");
    }
}
