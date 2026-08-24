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
public record ScoredAtom(
        UUID atomId, double score, double secondary, Components components) {

    /**
     * How close two relevance scores have to be before Bolum 19.4's secondary
     * criteria are allowed to decide between them.
     *
     * <p>Bolum 19.4 says "among atoms with close scores" and does not define
     * close. This does, as a bucket width: scores are rounded to a multiple of
     * this and the rounded value is the sort key. The weights of Bolum 19.1 are
     * hand-tuned to one decimal place, so two atoms within 0.02 of each other
     * are not meaningfully different.
     */
    public static final double RELEVANCE_BUCKET = 0.02;

    /**
     * Bolum 19.6 and Bolum 19.4 together, and the order of the keys is the
     * whole design.
     *
     * <p><strong>The bucket, not an epsilon.</strong> The obvious reading of
     * "close scores" is a comparator that consults the secondary criteria when
     * two scores are within some distance — and that comparator is not
     * transitive. With a ≈ b and b ≈ c but a ≢ c, {@code List.sort} detects the
     * inconsistency and throws {@code IllegalArgumentException: Comparison
     * method violates its general contract}, on a large profile, in
     * production, having passed every test. Rounding to a bucket makes
     * closeness an equivalence class instead of a relation, and the result is
     * an ordinary total order.
     *
     * <p>Relevance still dominates: a bucket apart is a bucket apart whatever
     * the secondary score says. Within one bucket, Bolum 19.4 decides —
     * recency, importance, impact, verification.
     *
     * <p>The id is last and still mandatory (Bolum 19.6). It is also now
     * reached far less often, which matters: ids are regenerated on every
     * import, so an ordering that leaned on them changed when the same content
     * was imported twice.
     */
    public static final Comparator<ScoredAtom> MOST_RELEVANT_FIRST =
            Comparator.comparingLong(ScoredAtom::relevanceBucket).reversed()
                    .thenComparing(Comparator.comparingDouble(ScoredAtom::secondary).reversed())
                    .thenComparing(atom -> atom.atomId().toString());

    /** The equivalence class this score falls in. */
    long relevanceBucket() {
        return Math.round(score / RELEVANCE_BUCKET);
    }

    public ScoredAtom {
        if (score < 0 || score > 1 || Double.isNaN(score)) {
            throw new IllegalArgumentException("A score is 0..1, got " + score);
        }
        if (secondary < 0 || secondary > 1 || Double.isNaN(secondary)) {
            throw new IllegalArgumentException("A secondary score is 0..1, got " + secondary);
        }
    }

    /** What each of Bolum 19.1's four terms contributed, before weighting. */
    public record Components(double embedding, double tag, double skill, double keyword) {
    }
}
