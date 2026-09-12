package com.mustafatetik.atomcv.shared.wire;

/**
 * How well the page answers the posting, as four named steps (Bolum 23.3).
 *
 * <p><strong>Four words and never a percentage.</strong> Bolum 23.3 opens by
 * forbidding one: a number to the decimal place claims a precision that
 * counting skill names cannot support, and it invites the reader to treat a
 * keyword tally as a hiring probability. The counts underneath are the honest
 * part; this is a heading over them.
 *
 * <p>The order is the severity order, so a client may compare with
 * {@link #compareTo} rather than hard-coding a table of its own.
 *
 * <p><strong>It lives in {@code shared} because two modules publish it</strong>
 * (F-032), and for the reason {@code ExtractionWarningCode} gives next door.
 * Faz F decides it and {@code GET /generations/&#123;id&#125;} carries it,
 * which is one module; then {@code GET /jobs/&#123;id&#125;} had to carry it
 * too, because the terminal SSE event is the raw result map and a client that
 * fell back to polling lost the heading over the counts. Generation already
 * depends on jobs to queue its work, so naming this type from there would have
 * closed the circle.
 */
public enum MatchLevel {

    /** Two or more required skills are missing. */
    WEAK,

    /** Exactly one required skill is missing. */
    MODERATE,

    /** Every requirement is covered. */
    GOOD,

    /** Every requirement is covered, and most of the nice-to-haves too. */
    STRONG
}
