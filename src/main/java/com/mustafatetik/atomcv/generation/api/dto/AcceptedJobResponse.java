package com.mustafatetik.atomcv.generation.api.dto;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * What a 202 carries (Bolum 35.3).
 *
 * <p>The id and nothing else the caller could have worked out. Where to watch
 * it is {@code Location}, and the progress stream of Bolum 30.6 arrives in the
 * next slice — until it does, {@code GET /jobs/{id}} is the way to follow a
 * generation, which EK D.6.4 already names as an acceptable fallback.
 */
@Schema(description = "A generation that was accepted and queued")
public record AcceptedJobResponse(

        @Schema(description = "Follow it at /api/v1/jobs/{jobId}")
        UUID jobId,

        JobStatus status) {

    public static AcceptedJobResponse of(Job job) {
        return new AcceptedJobResponse(job.getId(), job.getStatus());
    }
}
