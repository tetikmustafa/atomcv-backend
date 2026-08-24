package com.mustafatetik.atomcv.jobs.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * What the {@code phase} SSE event and the {@code jobs.progress} column
 * actually carry (F-010).
 *
 * <p>The shape is serialised in two places and neither of them is easy to look
 * at: one goes down a stream, the other into a JSONB column. So the assertion
 * is on the JSON text rather than on the record.
 */
class JobProgressJsonTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aqueuedJobSendsNoPhaseAtAllRatherThanAnEmptyOne() throws Exception {
        // The whole of F-010: an empty `label` is not a translation key, and a
        // client that did not special-case it printed `generation.phase.` on
        // the most-watched line in the product.
        assertThat(json.writeValueAsString(JobProgress.NONE))
                .isEqualTo("{\"pct\":0}");
    }

    @Test
    void apercentageOfZeroIsStillSent() throws Exception {
        // NON_EMPTY leaves primitives alone, but that is Jackson's decision
        // and not ours — an upgrade that changed it would silently drop the
        // percentage from every queued job. Asserted so it cannot.
        assertThat(json.writeValueAsString(new JobProgress("A", "generation.phase.ANALYSING", 0)))
                .contains("\"pct\":0");
    }

    @Test
    void arunningPhaseSendsEverythingItHas() throws Exception {
        var progress = new JobProgress("B", "generation.phase.SCORING", 50, "4/7");

        assertThat(json.writeValueAsString(progress)).isEqualTo(
                "{\"phase\":\"B\",\"label\":\"generation.phase.SCORING\","
                        + "\"pct\":50,\"detail\":\"4/7\"}");
    }

    @Test
    void afinishedJobSendsItsHundredAndNothingElse() throws Exception {
        // A completed job reporting the last phase it passed through reads as
        // "Rendering, 100%" — a progress bar arguing with the status beside it.
        assertThat(json.writeValueAsString(JobProgress.DONE))
                .isEqualTo("{\"pct\":100}");
    }

    @Test
    void arowWrittenByTheOlderBuildStillReadsBack() throws Exception {
        // The column holds what earlier builds wrote, empty strings included.
        // A fix that cannot read back its own history is not a fix.
        var stored = json.readValue(
                "{\"phase\":\"\",\"label\":\"\",\"pct\":0,\"detail\":\"\"}", JobProgress.class);

        assertThat(stored).isEqualTo(JobProgress.NONE);
    }
}
