package com.mustafatetik.atomcv.generation.scoring;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.Entry;
import java.time.LocalDate;

/**
 * Where selection gets its scores (Bolum 19.4).
 *
 * <p>The one thing that differs between a CV written against a posting and a
 * general one. Faz B and Faz C were separated exactly so that this could be a
 * parameter: general mode is a different score function and nothing else, and
 * the selection algorithm underneath does not know which it was given.
 */
@FunctionalInterface
public interface AtomScoreSource {

    /**
     * @param entry the atom's entry, or null for an atom that hangs straight
     *              off a section
     * @return the atom's score, between 0 and 1
     */
    double scoreOf(Atom atom, Entry entry);

    /**
     * No posting to be relevant to: rank on what the profile says about itself
     * (Bolum 19.4).
     *
     * @param today a parameter rather than a call to {@code now()}, because a
     *              scorer that reads the clock cannot be tested for the
     *              same-input-same-output property Bolum 51.2 requires
     */
    static AtomScoreSource generalMode(LocalDate today) {
        return (atom, entry) -> GeneralModeScorer.score(atom, entry, today);
    }
}
