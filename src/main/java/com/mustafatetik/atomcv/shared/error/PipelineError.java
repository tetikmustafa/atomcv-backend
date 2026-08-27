package com.mustafatetik.atomcv.shared.error;

import java.util.List;
import java.util.Objects;

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
     * plausibility gate on what came back. The catalogue still publishes one
     * code for all eight ways it happens, but no longer only two numbers —
     * {@code reason} says which check refused, because {@code confidence} and
     * {@code skillsFound} describe two of the eight and contradict the other
     * six (F-016). A gate refusal reporting {@code confidence 0.95} was read
     * out to users as "we could not read the posting; confidence 95%".
     *
     * @param confidence   what the model reported, or {@code 0} when the
     *                     preflight refused before anything was analysed
     * @param skillsFound  how many required skills the analysis produced,
     *                     likewise {@code 0} from the preflight
     * @param reason       which of the eight checks refused; never {@code null},
     *                     because a refusal with no reason is the bug this
     *                     parameter exists to close
     */
    record UnparseableJobDescription(
            double confidence, int skillsFound, UnreadablePostingReason reason)
            implements PipelineError {

        public UnparseableJobDescription {
            Objects.requireNonNull(reason, "reason");
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
     * The brake is on (Bolum 44.3).
     *
     * <p>Not the user's doing and not their problem to solve, which is why it
     * carries no parameters — there is nothing about their request to change.
     * It stops generation and nothing else: the profile stays readable,
     * editable and exportable, because losing a day's work to a cost spike
     * would be worse than the spike.
     */
    record GenerationPaused() implements PipelineError {
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

    /**
     * A CV whose language could not be settled (Bolum 31.10).
     *
     * <p>Bolum 31.10 says to ask rather than to guess, and the reason is that
     * the guess is not recoverable by the user: the language chosen here
     * decides which variant of every atom is written, so a wrong one produces
     * a whole profile in the wrong language and no screen that says so.
     *
     * @param candidates what the model was torn between, as ISO 639-1 codes,
     *                   so the question is a short list and not a language
     *                   picker. Empty when it offered nothing.
     */
    record LanguageUndetected(List<String> candidates) implements PipelineError {

        public LanguageUndetected {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    /**
     * A document that produced no usable content (Bolum 31.10).
     *
     * <p>No parameters, and one case covering two causes: a CV the model found
     * nothing in, and an answer refused by the field-length audit. Bolum 43.2
     * is why they are one — a message that named the second would tell whoever
     * wrote the injected text that it was noticed, and the advice is the same
     * either way.
     */
    record NothingExtracted() implements PipelineError {
    }

    /**
     * A translation that changed something it was not allowed to (Bolum 21.8).
     *
     * <p>A CV is a set of claims about a person, and a language change must not
     * change any of them. A wording that lost a number or renamed an employer
     * is refused rather than stored — it is the kind of alteration nobody
     * proofreads out, because the sentence still reads perfectly.
     *
     * <p>No parameters: what was dropped is the user's own content and may not
     * travel (absolute rule 4). The count reaches the operator through a log
     * line, and the person sees the wording stay marked stale, which is the
     * true statement about it.
     */
    record TranslationRejected() implements PipelineError {
    }

}
