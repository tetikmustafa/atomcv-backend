package com.mustafatetik.atomcv.generation.validation;

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
