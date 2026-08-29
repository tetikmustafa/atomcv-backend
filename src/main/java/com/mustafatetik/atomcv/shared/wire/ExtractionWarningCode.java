package com.mustafatetik.atomcv.shared.wire;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * What the review screen of Bolum 31.6 opens a section for.
 *
 * <p>Bolum 31.4 shows one code, {@code AMBIGUOUS_DATE}, and does not say
 * whether the vocabulary is closed. It is closed here, for the reason F-016
 * settled elsewhere in this system: the frontend resolves one ICU key with a
 * {@code select}, so a code it has never seen renders through the
 * {@code other} branch instead of showing a user a raw key — and a server
 * sentence would be untranslatable besides.
 *
 * <p>Only what a model can actually report is here. Bolum 31.5's normalisation
 * raises warnings of its own and will add its codes when it lands; guessing at
 * them now would mean guessing at their {@code path} and their meaning.
 *
 * <p><strong>A warning is not a refusal.</strong> Every value here describes a
 * field the person can correct on the review screen, which is why that screen
 * is mandatory. What cannot be corrected — no language, no content at all —
 * ends the extraction instead (Bolum 31.10).
 *
 * <p><strong>It lives in {@code shared} because two modules touch it</strong>
 * ({@code F-023}). Bolum 31 raises these codes and {@code GET /jobs/&#123;id&#125;}
 * publishes them, and ingestion already depends on jobs to queue its work — so
 * an import in the other direction would close a circle the architecture test
 * refuses. Its sibling {@code UnreadablePostingReason} moved for the same
 * reason. Nothing here depends on anything, which is what makes the move free.
 */
public enum ExtractionWarningCode {

    /** A date was written in a form that could not be read. The field is left null. */
    AMBIGUOUS_DATE,

    /** An entry has bullets but no employer, school or project name attached. */
    MISSING_ORGANIZATION,

    /** A heading did not match any of Bolum 13's kinds and was filed as custom. */
    UNCLEAR_SECTION,

    /**
     * The document appeared to be out of order.
     *
     * <p>Raised by the model, not by {@code ScrambleHeuristic}: the heuristic
     * decides whether to <em>warn the model</em>, and this says the model
     * agreed after reading it. The two disagree often — a one-column CV of
     * short lines trips the heuristic and reads perfectly.
     */
    SCRAMBLED_TEXT,

    /** Two entries claim overlapping dates in a way that may be a misreading. */
    OVERLAPPING_DATES,

    /**
     * A bullet could not be rendered into English.
     *
     * <p>Bolum 21 lets a document fall back to its source language atom by
     * atom, so this is a gap in coverage rather than a failure — but the user
     * should know which sentence will not travel.
     */
    UNTRANSLATABLE_ATOM;

    /** Lowercase on the wire, like every other closed vocabulary here (EK D.9). */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ExtractionWarningCode fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
