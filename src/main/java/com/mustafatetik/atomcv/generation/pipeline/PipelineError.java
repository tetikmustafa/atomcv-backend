package com.mustafatetik.atomcv.generation.pipeline;

import com.mustafatetik.atomcv.compilation.CompilationException;
import com.mustafatetik.atomcv.shared.error.Resolution;
import java.util.List;

/**
 * What can go wrong on the way to a CV (Bolum 25.2).
 *
 * <p>Sealed, so that presenting an error is an exhaustive switch: a new kind
 * of failure does not compile until someone has decided what the user is told
 * and what they can do about it. That is design principle 4 enforced by the
 * language rather than by discipline.
 *
 * <p>Only the cases the pipeline can produce today are here. The rest arrive
 * with the phases that raise them; adding one early would mean guessing at its
 * parameters, and the parameters are the part the frontend's messages need.
 */
public sealed interface PipelineError {

    /**
     * There is not enough profile to make a CV out of (Bolum 25.2).
     *
     * <p>Raised before anything is measured, rendered or compiled: the first
     * of the preflight checks design principle 5 asks for.
     *
     * @param completeness what the profile scores today, for the message
     * @param missing      which parts are absent, as field names — never the
     *                     user's own text (absolute rule 4)
     */
    record InsufficientProfile(int completeness, List<String> missing)
            implements PipelineError {

        public InsufficientProfile {
            missing = List.copyOf(missing);
        }
    }

    /**
     * Pinned content does not fit the page limit (Bolum 20.3, stage 1).
     *
     * <p>This is a conflict between two things the user asked for, so the
     * answer is not "no" but "here is what would make it fit". The resolutions
     * are computed from the selection that failed, not from a fixed list.
     *
     * @param pinnedPt what the locked content alone occupies
     * @param budgetPt what the page limit allows
     * @param options  what the user can do, in the order they should be offered
     */
    record ConflictingPreferences(double pinnedPt, double budgetPt, List<Resolution> options)
            implements PipelineError {

        public ConflictingPreferences {
            options = List.copyOf(options);
        }

        /** How many pages the pinned content would need, for the message. */
        public double pinnedPages(double pageHeightPt) {
            return pinnedPt / pageHeightPt;
        }
    }

    /**
     * The compiled document came out longer than the limit and shrinking the
     * budget did not save it (Bolum 23.1).
     *
     * <p>Reaching this means the measurement layer was optimistic by more than
     * the retries could absorb. It is a defect signal as much as a user
     * message, which is why the metric next to it is watched.
     *
     * @param actualPages what the compiler produced on the last attempt
     * @param maxPages    what the user asked for
     */
    record PageLimitExceeded(int actualPages, int maxPages) implements PipelineError {
    }

    /**
     * The document did not compile, or the compiler was not there (Bolum 29).
     *
     * @param kind   which of the four, so the caller knows whether to retry
     * @param texLog what TeX said — user content, never logged (absolute rule 4)
     */
    record CompilationFailed(CompilationException.Kind kind, String texLog)
            implements PipelineError {

        public CompilationFailed {
            texLog = texLog == null ? "" : texLog;
        }
    }

}
