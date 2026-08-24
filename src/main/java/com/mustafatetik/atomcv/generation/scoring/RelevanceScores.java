package com.mustafatetik.atomcv.generation.scoring;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.Entry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What Faz B produced for one posting (Bolum 19).
 *
 * <p>Both halves are kept. The ranking is what selection consumes; the weights
 * are what says whether the embedding component took part, and a deployment
 * that scored without vectors for a week would otherwise look like a prompt
 * problem (Bolum 28.4).
 *
 * <p>The components inside each {@link ScoredAtom} survive this far for
 * Faz F's honest report: "your page scored low" is worth little, "none of your
 * skills matched" is worth acting on.
 *
 * @param ranked most relevant first, ties broken by id (Bolum 19.6)
 * @param byAtom the same scores, indexed. A component rather than a method,
 *               because selection looks up every atom in the profile and
 *               rebuilding the index per lookup would make the build
 *               quadratic. Derived by the two-argument constructor, which is
 *               the one callers use.
 */
public record RelevanceScores(
        List<ScoredAtom> ranked, ScoringWeights weights, Map<UUID, Double> byAtom)
        implements AtomScoreSource {

    public RelevanceScores {
        ranked = List.copyOf(ranked);
        byAtom = Map.copyOf(byAtom);
    }

    public RelevanceScores(List<ScoredAtom> ranked, ScoringWeights weights) {
        this(ranked, weights, index(ranked));
    }

    /**
     * The score of one atom, ignoring the entry: relevance is a property of
     * what the atom says, not of where it sits.
     *
     * @return {@code 0} for an atom that was never scored. Bolum 19.5 leaves
     *         inactive atoms out, and selection rejects them as
     *         {@code INACTIVE} before the number is read — but if one ever did
     *         reach here, ranking last is the safe answer and refusing would
     *         fail a whole generation over a bookkeeping mismatch.
     */
    @Override
    public double scoreOf(Atom atom, Entry entry) {
        return byAtom.getOrDefault(atom.getId(), 0.0);
    }

    private static Map<UUID, Double> index(List<ScoredAtom> ranked) {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        for (ScoredAtom atom : ranked) {
            scores.put(atom.atomId(), atom.score());
        }
        return scores;
    }
}
