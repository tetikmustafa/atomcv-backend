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

    /** The store could not be read. The work is lost, and saying so is the point. */
    UNAVAILABLE;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
