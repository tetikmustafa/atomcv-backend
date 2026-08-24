package com.mustafatetik.atomcv.jobs.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;

/**
 * Where a job has got to (EK D.6.4).
 *
 * <p>Three fields are present only in their own terminal state:
 * {@code generationId} and {@code pageCount} when it completed, {@code error}
 * when it failed. That is deliberate and the frontend can rely on it — a
 * completed job with no generation id would be a success nobody can open.
 *
 * <p>{@code pageCount} is here because the stream is not the only way to a
 * result (F-008). A client that fell back to polling — the documented answer
 * to a stream that closed without a terminal event — could reach the
 * generation but not the number printed beside it.
 *
 * <p>Polling this is the documented fallback for a progress stream that closed
 * without a terminal event. Without it the failure mode is the one Bolum 4's
 * fourth principle forbids: a spinner turning forever over work that finished
 * a minute ago.
 *
 * @param phase the pipeline phase last reported, "A" through "G"
 * @param label a <strong>translation key</strong>, never a sentence — the
 *              frontend owns the words (Bolum 35.4)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A job's progress or its outcome")
public record JobStatusResponse(
        UUID jobId,
        JobStatus status,
        String phase,
        String label,
        int pct,
        String detail,
        UUID generationId,
        Integer pageCount,
        Map<String, Object> error) {

    public static JobStatusResponse of(Job job) {
        var progress = job.getProgress();
        return new JobStatusResponse(
                job.getId(),
                job.getStatus(),
                blankToNull(progress.phase()),
                blankToNull(progress.label()),
                progress.pct(),
                blankToNull(progress.detail()),
                generationIdOf(job),
                pageCountOf(job),
                job.getStatus() == JobStatus.FAILED ? job.getError() : null);
    }

    private static UUID generationIdOf(Job job) {
        if (job.getStatus() != JobStatus.COMPLETED || job.getResult() == null) {
            return null;
        }
        Object id = job.getResult().get("generationId");
        return id instanceof String text ? UUID.fromString(text) : null;
    }

    private static Integer pageCountOf(Job job) {
        if (job.getStatus() != JobStatus.COMPLETED || job.getResult() == null) {
            return null;
        }
        return job.getResult().get("pageCount") instanceof Number pages
                ? pages.intValue() : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
