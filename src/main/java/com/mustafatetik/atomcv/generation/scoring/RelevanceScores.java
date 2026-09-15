package com.mustafatetik.atomcv.generation.scoring;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.Entry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What Faz B produced for one posting.
 *
 * <p>Both halves are kept. The ranking is what selection consumes; the weights
 * are what says whether the embedding component took part, and a deployment
 * that scored without vectors for a week would otherwise look like a prompt
 * problem.
 *
 * <p>The components inside each {@link ScoredAtom} survive this far for
 * Faz F's honest report: "your page scored low" is worth little, "none of your
 * skills matched" is worth acting on.
 *
 * @param ranked most relevant first, ties broken by id
 * @param byAtom the same scores, indexed. A component rather than a method,
 *               because selection looks up every atom in the profile and
 *               rebuilding the index per lookup would make the build
 *               quadratic. Derived by the two-argument constructor, which is
 *               the one callers use.
 */
public record RelevanceScores(
        List<ScoredAtom> ranked, ScoringWeights weights, Map<UUID, Double> byAtom,
        Map<UUID, List<String>> matchedByAtom)
        implements AtomScoreSource {

    public RelevanceScores {
        ranked = List.copyOf(ranked);
        byAtom = Map.copyOf(byAtom);
        matchedByAtom = Map.copyOf(matchedByAtom);
    }

    public RelevanceScores(
            List<ScoredAtom> ranked, ScoringWeights weights, Map<UUID, Double> byAtom) {
        this(ranked, weights, byAtom, matches(ranked));
    }

    public RelevanceScores(List<ScoredAtom> ranked, ScoringWeights weights) {
        this(ranked, weights, index(ranked), matches(ranked));
    }

    /**
     * The other half of the reason: not how well this atom scored, but what it
     * matched.
     *
     * <p>Faz C copies it onto every selected line so that the answer survives
     * into {@code selection_state} -- a generation read back next week has no
     * posting analysis in scope and cannot recompute it — the same argument
     * that keeps the score here.
     */
    @Override
    public List<String> matchedTermsOf(Atom atom) {
        return matchedByAtom.getOrDefault(atom.getId(), List.of());
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

    private static Map<UUID, List<String>> matches(List<ScoredAtom> ranked) {
        // Only atoms that matched something are indexed: against a posting of
        // any size most of a profile matches nothing, and an entry per atom
        // would be a map of empty lists.
        //
        // The map is read by key and never iterated, so the copy the compact
        // constructor makes is free to reorder it. The *lists* are the part
        // that has to be stable, and RelevanceScorer sorts them for exactly
        // that reason.
        Map<UUID, List<String>> matched = new LinkedHashMap<>();
        for (ScoredAtom atom : ranked) {
            if (!atom.matchedTerms().isEmpty()) {
                matched.put(atom.atomId(), atom.matchedTerms());
            }
        }
        return matched;
    }

    private static Map<UUID, Double> index(List<ScoredAtom> ranked) {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        for (ScoredAtom atom : ranked) {
            scores.put(atom.atomId(), atom.score());
        }
        return scores;
    }
}
