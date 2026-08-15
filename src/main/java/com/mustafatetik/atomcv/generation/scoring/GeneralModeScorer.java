package com.mustafatetik.atomcv.generation.scoring;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.Entry;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Scoring with no job description to score against (Bolum 19.4).
 *
 * <p>The general CV mode skips Faz A and Faz B's relevance components — there
 * is nothing to be relevant to — and ranks on what the profile says about
 * itself: how recent it is, how important the user marked it, whether it
 * carries a number, and whether it has been verified.
 *
 * <p>Pure and deterministic, like everything Faz C stands on. Today's date is
 * a parameter rather than a call to {@code now()} for that reason: a scorer
 * that reads the clock cannot be tested for the same-input-same-output
 * property that Bolum 51.2 requires.
 */
public final class GeneralModeScorer {

    /**
     * How long it takes for an entry's recency to halve.
     *
     * <p>Bolum 19.4 asks for exponential decay and does not give a rate. Five
     * years puts a job from a decade ago at a quarter of a current one, which
     * matches the advice CVs are actually written by; it does not put it at
     * zero, because a decade-old bullet with a metric in it can still be the
     * best thing on the page (EK D.8.7).
     */
    static final double HALF_LIFE_YEARS = 5.0;

    /** Bolum 19.4: a bullet with a number in it says more than one without. */
    static final double IMPACT_WITHOUT_METRICS = 0.3;

    private GeneralModeScorer() {
    }

    /**
     * @param entry the atom's entry, or {@code null} for an atom that hangs
     *              straight off a section — a skill has no dates and is not
     *              made less relevant by that
     */
    public static double score(Atom atom, Entry entry, LocalDate today) {
        // Clamped: the weights add up to one, but four rounded doubles can add
        // up to 1.0000000000000002, and AtomCandidate refuses a score above
        // one — a defect that would only appear for a perfect atom.
        return clamp(0.35 * recency(entry, today)
                + 0.30 * clamp(atom.getImportance())
                + 0.20 * impact(atom)
                + 0.15 * (atom.isVerified() ? 1.0 : 0.0));
    }

    static double recency(Entry entry, LocalDate today) {
        if (entry == null || entry.getEndDate() == null) {
            // Still going, or never had dates at all.
            return 1.0;
        }
        double years = ChronoUnit.DAYS.between(entry.getEndDate(), today) / 365.2425;
        if (years <= 0) {
            // An end date in the future is a plan, not a mistake to punish.
            return 1.0;
        }
        return Math.pow(0.5, years / HALF_LIFE_YEARS);
    }

    static double impact(Atom atom) {
        return atom.getMetrics().isEmpty() ? IMPACT_WITHOUT_METRICS : 1.0;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
