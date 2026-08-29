package com.mustafatetik.atomcv.jobs.api.dto;

import com.mustafatetik.atomcv.shared.wire.ExtractionWarningCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where a job has got to (EK D.6.4).
 *
 * <p>Fields are present only in their own terminal state:
 * {@code generationId} and {@code pageCount} when a generation completed,
 * the import block when an import did, {@code error} when either failed. That
 * is deliberate and the frontend can rely on it — a completed job with no
 * generation id would be a success nobody can open.
 *
 * <p><strong>The import's result is here for the reason {@code pageCount}
 * is</strong> (F-018). The terminal SSE event carried it and nothing else did,
 * so a client that reloaded the page had no way to learn what the import
 * produced — the same failure {@code F-008} found on the generation side, on
 * the other job type. {@code warnings} carries the positions Bolum 31.6's
 * review screen opens on: a count could say two sections needed attention and
 * not which two.
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

        @Schema(description = "An import's profile, when one completed")
        UUID profileId,

        @Schema(description = "How many sections the import wrote")
        Integer sectionCount,

        @Schema(description = "How many atoms the import wrote")
        Integer atomCount,

        @Schema(description = "How many things could not be settled; the same "
                + "number as `warnings.length`")
        Integer warningCount,

        @Schema(description = "The language the CV was read as, ISO 639-1")
        String detectedLanguage,

        @Schema(description = "What could not be settled, and where. Absent "
                + "for a job that is not an import.")
        List<ImportWarning> warnings,

        Map<String, Object> error) {

    /**
     * One thing the import could not settle, and the row it is about.
     *
     * <p><strong>Positions, not ids, and they are enough.</strong>
     * {@code sectionOrder} and {@code entryOrder} are the {@code displayOrder}
     * that {@code GET /profile} already publishes on both, so a client
     * resolves a warning against the profile it has just fetched without this
     * endpoint reading the rows back to name them.
     *
     * <p>Both are absent on a warning that names no entry — the model raises
     * some of those, and a document-level warning is not a broken one.
     *
     * <p>No {@code detail}: it is an English note written for an operator, and
     * the {@code code} is the closed vocabulary the frontend renders an ICU
     * message from.
     *
     * <p><strong>The schema is the enum; the field is a String</strong>
     * ({@code F-023}). Publishing {@link ExtractionWarningCode} is what lets a
     * client write six messages instead of guessing at them — the frontend
     * could name none of the six from a bare {@code string}, and a guessed key
     * set would be six lines that never match. The value is still read as text
     * because it comes back through a JSONB column: a row written before a
     * value was renamed carries a code this build does not know, and typing
     * the field would make that row either throw or vanish. A vanished warning
     * would break {@code warningCount == warnings.length}, so it travels, and
     * a client reading a closed vocabulary openly falls to its own general
     * sentence.
     *
     * @param code one of Bolum 31.4's vocabulary, lowercase on the wire
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Something the import could not settle")
    public record ImportWarning(
            @Schema(implementation = ExtractionWarningCode.class) String code,
            Integer sectionOrder,
            Integer entryOrder) {
    }

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
                uuidResult(job, "profileId"),
                intResult(job, "sectionCount"),
                intResult(job, "atomCount"),
                intResult(job, "warningCount"),
                stringResult(job, "detectedLanguage"),
                warningsOf(job),
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

    private static Map<String, Object> completedResult(Job job) {
        return job.getStatus() == JobStatus.COMPLETED && job.getResult() != null
                ? job.getResult() : Map.of();
    }

    private static UUID uuidResult(Job job, String key) {
        return completedResult(job).get(key) instanceof String text ? UUID.fromString(text) : null;
    }

    private static Integer intResult(Job job, String key) {
        return completedResult(job).get(key) instanceof Number number ? number.intValue() : null;
    }

    private static String stringResult(Job job, String key) {
        return completedResult(job).get(key) instanceof String text ? blankToNull(text) : null;
    }

    /**
     * <p>Read defensively because this comes back through a JSONB column: a
     * row written before the field existed has no {@code warnings} at all, and
     * an import that raised none has an empty list. Both are "no warnings" to
     * a client, and neither is a failure to read the row.
     */
    private static List<ImportWarning> warningsOf(Job job) {
        if (!(completedResult(job).get("warnings") instanceof List<?> raw)) {
            return null;
        }
        List<ImportWarning> warnings = new ArrayList<>(raw.size());
        for (Object each : raw) {
            if (each instanceof Map<?, ?> warning
                    && warning.get("code") instanceof String code) {
                warnings.add(new ImportWarning(code,
                        asInt(warning.get("sectionOrder")), asInt(warning.get("entryOrder"))));
            }
        }
        return List.copyOf(warnings);
    }

    private static Integer asInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
