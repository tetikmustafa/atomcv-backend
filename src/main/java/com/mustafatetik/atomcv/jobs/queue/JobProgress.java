package com.mustafatetik.atomcv.jobs.queue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
 * @param phase the pipeline phase, "A" through "G"
 * @param pct   0 to 100
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobProgress(String phase, String label, int pct, String detail) {

    /** A job that has been queued but not started. */
    public static final JobProgress NONE = new JobProgress("", "", 0, "");

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
