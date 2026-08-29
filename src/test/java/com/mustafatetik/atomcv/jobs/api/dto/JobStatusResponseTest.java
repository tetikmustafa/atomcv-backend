package com.mustafatetik.atomcv.jobs.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What polling a job can learn (F-018, EK D.6.4).
 *
 * <p>The stream is not the only way to a result — a client that reloaded the
 * page has only this — and until now it could say nothing at all about an
 * import. That is the failure {@code F-008} found on the generation side, on
 * the other job type.
 */
class JobStatusResponseTest {

    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    /**
     * The whole of what Bolum 31.6's screen opens on. A count could say two
     * sections needed attention; it could not say which two, and both of that
     * section's design rules need to know which.
     */
    @Test
    void acompletedImportPublishesItsResultAndWhereTheWarningsAre() {
        var result = new LinkedHashMap<String, Object>();
        UUID profileId = UUID.randomUUID();
        result.put("profileId", profileId.toString());
        result.put("sectionCount", 5);
        result.put("atomCount", 42);
        result.put("warningCount", 2);
        result.put("detectedLanguage", "tr");
        result.put("warnings", List.of(
                Map.of("code", "ambiguous_date", "sectionOrder", 1, "entryOrder", 0),
                Map.of("code", "scrambled_text")));

        var response = JobStatusResponse.of(completed(JobType.PROFILE_EXTRACT, result));

        assertThat(response.profileId()).isEqualTo(profileId);
        assertThat(response.sectionCount()).isEqualTo(5);
        assertThat(response.atomCount()).isEqualTo(42);
        assertThat(response.warningCount()).isEqualTo(2);
        assertThat(response.detectedLanguage()).isEqualTo("tr");

        assertThat(response.warnings()).hasSize(2);
        assertThat(response.warnings().get(0))
                .isEqualTo(new JobStatusResponse.ImportWarning("ambiguous_date", 1, 0));
        // A warning naming no entry travels with its code and no position:
        // the model raises some, and "something about this document" is still
        // worth a count.
        assertThat(response.warnings().get(1))
                .isEqualTo(new JobStatusResponse.ImportWarning("scrambled_text", null, null));
    }

    /**
     * A generation job answers about a generation and nothing about an import.
     * The fields are present only in their own terminal state, which is what
     * lets a client tell the two job types apart from the body alone.
     */
    @Test
    void agenerationJobCarriesNoImportFields() {
        var result = new LinkedHashMap<String, Object>();
        result.put("generationId", UUID.randomUUID().toString());
        result.put("pageCount", 2);

        var response = JobStatusResponse.of(completed(JobType.GENERATION, result));

        assertThat(response.pageCount()).isEqualTo(2);
        assertThat(response.profileId()).isNull();
        assertThat(response.sectionCount()).isNull();
        assertThat(response.warnings()).isNull();
    }

    /**
     * Read defensively, because this comes back through a JSONB column: a row
     * written before the field existed has no {@code warnings} key at all, and
     * that is a job to report rather than a row to fail on.
     */
    @Test
    void arowWrittenBeforeWarningsExistedStillReads() {
        var result = new LinkedHashMap<String, Object>();
        result.put("profileId", UUID.randomUUID().toString());
        result.put("sectionCount", 3);

        var response = JobStatusResponse.of(completed(JobType.PROFILE_EXTRACT, result));

        assertThat(response.sectionCount()).isEqualTo(3);
        assertThat(response.warnings()).isNull();
    }

    /** An unfinished job has no result to report, whatever is on the row. */
    @Test
    void ajobThatHasNotFinishedPublishesNoResult() {
        var job = new Job(JobType.PROFILE_EXTRACT, UUID.randomUUID(), Map.of(), NOW);

        var response = JobStatusResponse.of(job);

        assertThat(response.profileId()).isNull();
        assertThat(response.warnings()).isNull();
    }

    private static Job completed(JobType type, Map<String, Object> result) {
        var job = new Job(type, UUID.randomUUID(), Map.of(), NOW);
        job.succeed(result, NOW);
        return job;
    }
}
