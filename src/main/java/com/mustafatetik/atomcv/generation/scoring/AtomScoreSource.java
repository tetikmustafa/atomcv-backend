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
     * What an entry with no atoms at all is worth (Bolum 20.2).
     *
     * <p>A degree line competes for the page against bullets, so it needs a
     * number on the same scale. There is no atom to ask, and against a posting
     * there is nothing to be relevant to either — Faz B scores wordings, and
     * this entry has none — so the default is the entry's own importance,
     * which is the one entry-level signal the user actually sets. General mode
     * overrides it, because there it can also ask how recent the entry is.
     *
     * @return between 0 and 1
     */
    default double scoreOfEntry(Entry entry) {
        return entry == null ? 0.0 : Math.max(0.0, Math.min(1.0, entry.getImportance()));
    }

    /**
     * No posting to be relevant to: rank on what the profile says about itself
     * (Bolum 19.4).
     *
     * @param today a parameter rather than a call to {@code now()}, because a
     *              scorer that reads the clock cannot be tested for the
     *              same-input-same-output property Bolum 51.2 requires
     */
    static AtomScoreSource generalMode(LocalDate today) {
        // Not a lambda: general mode answers both questions, and a lambda can
        // only answer the abstract one.
        return new AtomScoreSource() {

            @Override
            public double scoreOf(Atom atom, Entry entry) {
                return GeneralModeScorer.score(atom, entry, today);
            }

            @Override
            public double scoreOfEntry(Entry entry) {
                return GeneralModeScorer.scoreOfEntry(entry, today);
            }
        };
    }
}
