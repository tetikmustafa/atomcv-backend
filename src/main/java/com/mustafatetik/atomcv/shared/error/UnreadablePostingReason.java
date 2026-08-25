package com.mustafatetik.atomcv.shared.error;

import java.util.Locale;

/**
 * Why a posting could not be turned into an analysis (Bolum 18.1, Bolum 18.4).
 *
 * <p>Eight reasons behind one code. The catalogue publishes
 * {@link ErrorCode#UNPARSEABLE_JOB_DESCRIPTION} and keeps publishing it: from
 * the API's point of view the outcome is the same, and an eighth sibling code
 * would buy nothing the frontend cannot do with a parameter. What it could not
 * do without one is write a true sentence — {@code confidence} and
 * {@code skillsFound} describe two of these eight, and next to the other six
 * they contradict the refusal they arrive with (F-016).
 *
 * <p>The first four come from the preflight, before anything is spent; the last
 * four from the plausibility gate, on what came back. The split matters to the
 * user and not only to a log: the preflight refused <em>their text</em> and
 * they may know better than the heuristic, while the gate refused <em>the
 * model's answer</em> and there is nothing about the text to fix.
 *
 * <p>It lives in {@code shared} for {@link CompilationFailureKind}'s reason:
 * {@link PipelineError.UnparseableJobDescription} carries it, and {@code
 * shared} may not depend on a business module (Bolum 10.2, rule 4).
 */
public enum UnreadablePostingReason {

    // ── Preflight, Bolum 18.1. No call was made, so nothing was measured. ──

    /** Fewer characters or words than there is anything to analyse in. */
    TOO_SHORT(Origin.PREFLIGHT),

    /** A page rather than a posting. */
    TOO_LONG(Origin.PREFLIGHT),

    /** Too few distinct words to be prose — a paste loop, a wall of one phrase. */
    LOW_ENTROPY(Origin.PREFLIGHT),

    /** Prose, but nothing in it reads like a job posting. */
    NOT_JOB_LIKE(Origin.PREFLIGHT),

    // ── Plausibility gate, Bolum 18.4. An answer came back and was judged. ──

    /** The model reported it was guessing. */
    LOW_CONFIDENCE(Origin.GATE),

    /** Fewer than two required skills: nothing to score a profile against. */
    TOO_FEW_SKILLS(Origin.GATE),

    /** No responsibilities: Faz B has nothing to match bullets to. */
    NO_RESPONSIBILITIES(Origin.GATE),

    /**
     * A field is far longer than that field ever is.
     *
     * <p>The one reason that is not about the posting at all. The first seven
     * say the text was thin or absent; this one says the answer is not shaped
     * like an analysis — so the way out is to ask again, not to edit anything.
     */
    SUSPICIOUS_OUTPUT(Origin.GATE);

    /** Which of the two checks refused. Declared, not inferred from order. */
    private enum Origin { PREFLIGHT, GATE }

    private final Origin origin;

    UnreadablePostingReason(Origin origin) {
        this.origin = origin;
    }

    /** True when the reason is the model's answer rather than the user's text. */
    public boolean isFromGate() {
        return origin == Origin.GATE;
    }

    /** Lowercase on the wire, as {@code COMPILATION_FAILED.detail} publishes its kind. */
    public String wireValue() {
        // Locale.ROOT: absolute rule 7. Under a Turkish locale LOW_ENTROPY
        // lowercases to "low_entropy" but NOT_JOB_LIKE keeps a dotless "ı",
        // and no frontend key would match it.
        return name().toLowerCase(Locale.ROOT);
    }
}
