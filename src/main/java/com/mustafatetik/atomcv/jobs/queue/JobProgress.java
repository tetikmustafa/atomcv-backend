package com.mustafatetik.atomcv.jobs.queue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * How far along a job is, as {@code jobs.progress} holds it and as the
 * {@code phase} SSE event carries it (Bolum 30.6).
 *
 * <p>One shape for the column and the event, because they are the same fact:
 * the row is what a reconnecting client is caught up from, and a second shape
 * would be a second chance for the two to disagree.
 *
 * <p><strong>No user content.</strong> {@code label} is a translation key's
 * worth of fixed text written by the phase, and {@code detail} is a count like
 * "4/7" — never a bullet, a company name or anything off the posting
 * (absolute rule 4).
 *
 * <p><strong>An empty string is not sent (F-010).</strong> A queued job has no
 * phase yet, and {@code {"phase":"","label":""}} is worse than no field at
 * all: {@code label} is a translation key, the empty string is not one, and a
 * client that did not special-case it would look up {@code generation.phase.}
 * and print a raw key on the product's most-watched line. Absence says
 * "nothing to show" in a way every client already handles — and it is what
 * {@code GET /jobs/{id}} was already sending, so the stream and the poll now
 * agree.
 *
 * @param phase the pipeline phase, "A" through "G"
 * @param pct   0 to 100. Always sent, including zero: a progress bar with no
 *              percentage is not the same as one at the start.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record JobProgress(
        String phase,
        String label,
        @JsonInclude(JsonInclude.Include.ALWAYS) int pct,
        String detail) {

    /** A job that has been queued but not started. */
    public static final JobProgress NONE = new JobProgress("", "", 0, "");

    /**
     * A job that finished.
     *
     * <p>The phase and the label are cleared rather than kept. A completed job
     * reporting the last phase it passed through reads as "Rendering, 100%",
     * which is a progress bar arguing with the status beside it; and the
     * client's terminal state comes from {@code status} and
     * {@code generationId}, not from a phase name.
     *
     * <p>A <em>failure</em> keeps its progress on purpose. Where it stopped is
     * the most useful thing the row can say about why.
     */
    public static final JobProgress DONE = new JobProgress("", "", 100, "");

    public JobProgress {
        phase = phase == null ? "" : phase;
        label = label == null ? "" : label;
        detail = detail == null ? "" : detail;
        if (pct < 0 || pct > 100) {
            throw new IllegalArgumentException("A percentage is 0..100, was " + pct);
        }
    }

    public JobProgress(String phase, String label, int pct) {
        this(phase, label, pct, "");
    }
}
