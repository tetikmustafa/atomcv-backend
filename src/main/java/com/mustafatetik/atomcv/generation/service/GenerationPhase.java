package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.jobs.queue.JobProgress;

/**
 * The phases a queued generation reports as it passes them (Bolum 30.6).
 *
 * <p><strong>The label is a translation key, not a sentence.</strong>
 * Bolum 30.6's example carries prose, and that is the one place it disagrees
 * with Bolum 35.4: the server sends keys and the frontend owns the words. A
 * sentence here would be shipped in one language and re-shipped for every new
 * one, and the progress line is the most-seen text in the product.
 *
 * <p>Only the phases this pipeline can honestly say it reached. Faz D is
 * reported when it actually runs and not before: general mode has no posting
 * to write towards, and even against one there may be nothing worth
 * rewriting. Faz F's own steps are inside one compile loop, so the last thing
 * reported before the terminal event is that rendering started.
 */
public enum GenerationPhase {

    /** Faz A: reading the posting. */
    ANALYSING("A"),

    /** Bolum 26.2: measuring whatever has no render cost yet. */
    MEASURING("B"),

    /** Faz B: scoring the profile against the posting. */
    SCORING("B"),

    /** Faz D: rewriting the bullets that are worth it (Bolum 21.2). */
    REWRITING("D"),

    /** Faz C to F: choosing, rendering, compiling, checking the page count. */
    RENDERING("C");

    private final String phase;

    GenerationPhase(String phase) {
        this.phase = phase;
    }

    /** The key the frontend resolves, for example {@code generation.phase.SCORING}. */
    public String labelKey() {
        return "generation.phase." + name();
    }

    public JobProgress at(int pct) {
        return new JobProgress(phase, labelKey(), pct);
    }
}
