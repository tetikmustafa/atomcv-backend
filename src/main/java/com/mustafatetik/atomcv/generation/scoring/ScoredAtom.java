package com.mustafatetik.atomcv.generation.scoring;

import java.util.Comparator;
import java.util.UUID;

/**
 * One atom's relevance, with the parts it was made of (Bolum 19).
 *
 * <p>The components are kept rather than discarded because Faz F reports
 * honestly: telling a user their page scored low is worth little, telling them
 * it scored low because none of their skills matched is worth acting on. They
 * are also the only way to see, from a production sample, that one component
 * has stopped contributing.
 */
public record ScoredAtom(UUID atomId, double score, Components components) {

    /**
     * Bolum 19.6, and it is mandatory.
     *
     * <p>Ties are common — two bullets with the same tags and no vectors score
     * identically — and without a second key the sort is unstable across runs.
     * The id as a <em>string</em> rather than as a UUID, because
     * {@link UUID#compareTo} compares two signed longs and orders differently
     * from every other place an id is sorted.
     */
    public static final Comparator<ScoredAtom> MOST_RELEVANT_FIRST =
            Comparator.comparingDouble(ScoredAtom::score).reversed()
                    .thenComparing(atom -> atom.atomId().toString());

    public ScoredAtom {
        if (score < 0 || score > 1 || Double.isNaN(score)) {
            throw new IllegalArgumentException("A score is 0..1, got " + score);
        }
    }

    /** What each of Bolum 19.1's four terms contributed, before weighting. */
    public record Components(double embedding, double tag, double skill, double keyword) {
    }
}
