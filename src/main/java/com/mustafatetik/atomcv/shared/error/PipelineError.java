package com.mustafatetik.atomcv.shared.error;

import java.util.List;

/**
 * What can go wrong on the way to a CV (Bolum 25.2).
 *
 * <p>Sealed, so that presenting an error is an exhaustive switch: a new kind
 * of failure does not compile until someone has decided what the user is told
 * and what they can do about it. That is design principle 4 enforced by the
 * language rather than by discipline.
 *
 * <p>It lives in {@code shared} rather than in {@code generation.pipeline},
 * because Bolum 27.1 has {@code LlmProvider} return a {@code Result} of it and
 * {@code generation} depends on {@code llm} in turn — the two together are a
 * cycle. Bolum 10.1 already places this type here; what kept it out was
 * {@code CompilationFailed} naming a type from the {@code compilation} module,
 * and that is now {@link CompilationFailureKind}.
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
     * The posting could not be read as one (Bolum 18.1, Bolum 18.4).
     *
     * <p>Both gates raise it: the preflight before any call is made, and the
     * plausibility gate on what came back. The two are distinguishable inside
     * the phase and not on the wire, because the catalogue publishes one code.
     *
     * @param confidence   what the model reported, or {@code 0} when the
     *                     preflight refused before anything was analysed
     * @param skillsFound  how many required skills the analysis produced,
     *                     likewise {@code 0} from the preflight
     */
    record UnparseableJobDescription(double confidence, int skillsFound)
            implements PipelineError {
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
     * Every provider in the chain was tried and none of them answered
     * (Bolum 25.2, Bolum 27.3).
     *
     * <p>The only LLM failure a user is ever shown. A single provider's 429 or
     * schema mismatch has no code in the catalogue and stays inside the llm
     * module as an {@code LlmFailure}; this is what the chain reports once it
     * has run out of places to ask.
     *
     * @param tried the providers that were actually called, in order. One that
     *              was skipped for having no key is not in the list — Bolum
     *              27.3 skips it silently, and naming it would report an
     *              outage for a vendor this deployment never configured.
     */
    record AllProvidersUnavailable(List<String> tried) implements PipelineError {

        public AllProvidersUnavailable {
            tried = List.copyOf(tried);
        }
    }

    /**
     * The user has used up today's allowance (Bolum 44.1).
     *
     * <p>Raised before anything is queued, because the whole point of a quota
     * is that it costs nothing to enforce. Never retryable: the next attempt
     * reads the same counter, and a retry budget spent against a limit is
     * three failures instead of one.
     *
     * @param metric   which allowance ran out — there are two, and a single
     *                 one would let profile extraction eat the whole of it
     * @param resetsAt an absolute instant, not an hour (F-007). The day
     *                 boundary is UTC and the client writes the local text.
     */
    record QuotaExceeded(String metric, java.time.Instant resetsAt) implements PipelineError {
    }

    /**
     * The document did not compile, or the compiler was not there (Bolum 29).
     *
     * @param kind   which of the four, so the caller knows whether to retry
     * @param texLog what TeX said — user content, never logged (absolute rule 4)
     */
    record CompilationFailed(CompilationFailureKind kind, String texLog)
            implements PipelineError {

        public CompilationFailed {
            texLog = texLog == null ? "" : texLog;
        }
    }

}
