package com.mustafatetik.atomcv.generation.scoring;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.Entry;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

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

    /**
     * The scores a finished generation already paid for (Bolum 24.1).
     *
     * <p>Faz G re-runs the pipeline <em>from Faz C</em>, which is the whole
     * reason an edit is cheap: Faz A read the posting once and Faz B ranked
     * the profile against it once, and switching one bullet off changes
     * neither answer. The numbers come back out of
     * {@code generations.selection_state}, where every candidate is recorded —
     * chosen or rejected — with the score it competed on.
     *
     * <p>Entry headings are in the same map and need no special case: a
     * heading competes as a candidate whose id <em>is</em> the entry's
     * (Bolum 20.2), so it is stored under that id and looked up under it.
     *
     * <p><strong>Zero for anything the snapshot never scored.</strong> An atom
     * written after the generation was made was not part of this CV's world
     * and does not get to walk into it by being new; if the person wants it
     * there they can ask for it by name, and a directive outranks a score.
     * The alternative is re-running Faz B, which is the thing Bolum 24.1 says
     * not to do.
     *
     * @param byId score by atom id, and by entry id for a heading
     */
    static AtomScoreSource remembered(Map<UUID, Double> byId) {
        Map<UUID, Double> scores = Map.copyOf(byId);
        return new AtomScoreSource() {

            @Override
            public double scoreOf(Atom atom, Entry entry) {
                return scores.getOrDefault(atom.getId(), 0.0);
            }

            @Override
            public double scoreOfEntry(Entry entry) {
                return entry == null ? 0.0 : scores.getOrDefault(entry.getId(), 0.0);
            }
        };
    }
}
