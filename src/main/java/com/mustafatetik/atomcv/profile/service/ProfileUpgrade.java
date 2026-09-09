package com.mustafatetik.atomcv.profile.service;

import java.util.Locale;

/**
 * What became of the anonymous profile when its owner signed in (Adim 3.6).
 *
 * <p>Four answers rather than a boolean, because the three that are not
 * {@link #UPGRADED} lead to three different sentences. "There was nothing to
 * move" is the ordinary case and needs no sentence at all; "your account
 * already had a profile" is work the person can see is missing and must be
 * told about; "we could not reach it" is neither, and saying "nothing to move"
 * to somebody who just uploaded a CV would be a lie the product tells about
 * its own outage.
 */
public enum ProfileUpgrade {

    /** The anonymous profile is now the account's, ids and all. */
    UPGRADED,

    /** There was no anonymous profile — the ordinary sign-in. */
    NONE,

    /**
     * The account already had a profile, so nothing was written. The anonymous
     * one is left to its TTL: merging two CVs is a product decision nobody has
     * made, and overwriting months of editing with two hours of it is the
     * opposite of what design principle 8 asks for.
     */
    KEPT_EXISTING,

    /**
     * The hand-over failed. The work is lost, and saying so is the point.
     *
     * <p>It used to mean "Redis could not be read", and now that the profile is
     * a row it means the write did not go through — a lock timeout, a constraint
     * the delete-then-adopt sequence hit. Rarer, and still worth a value of its
     * own: {@link #NONE} says there was nothing to carry, and telling somebody
     * that when there was is the one wrong answer here.
     *
     * <p>Produced by {@code SignInHandover} rather than by the upgrade itself.
     * A transaction that has failed cannot report on itself and keep going —
     * catching inside it would leave the caller committing a rollback-only
     * transaction — so the catch belongs to the bean that calls it.
     */
    UNAVAILABLE;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
