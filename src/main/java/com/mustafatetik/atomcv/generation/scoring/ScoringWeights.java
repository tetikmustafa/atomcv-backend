package com.mustafatetik.atomcv.generation.scoring;

/**
 * How much each component of relevance counts (Bolum 19.1).
 *
 * <p>They sum to one, and that is checked: a set that did not would still
 * produce a ranking, just one whose scores could not be compared against
 * another generation's or read as a fraction in Faz F's honest report.
 *
 * @param embedding meaning, and the largest share — it is the only component
 *                  that sees a bullet the posting never named
 * @param tag       the profile's own vocabulary against the posting's
 * @param skill     canonical skills, weighted by whether the posting called
 *                  them required or preferred
 * @param keyword   the posting's literal phrases, worth least because they are
 *                  the easiest to match by accident
 */
public record ScoringWeights(double embedding, double tag, double skill, double keyword) {

    /** Bolum 19.1, verbatim. */
    public static final ScoringWeights DEFAULT = new ScoringWeights(0.40, 0.25, 0.25, 0.10);

    /**
     * Bolum 28.4: the embedding service is down, so its share is redistributed
     * and the rest of scoring carries on.
     *
     * <p>Quality drops and the user is not told — it is an internal detail —
     * but it is recorded, because a deployment that silently scored without
     * embeddings for a week would look like a prompt problem.
     */
    public static final ScoringWeights WITHOUT_EMBEDDING =
            new ScoringWeights(0.0, 0.42, 0.42, 0.16);

    private static final double TOLERANCE = 1e-9;

    public ScoringWeights {
        requireFraction(embedding, "embedding");
        requireFraction(tag, "tag");
        requireFraction(skill, "skill");
        requireFraction(keyword, "keyword");
        double total = embedding + tag + skill + keyword;
        if (Math.abs(total - 1.0) > TOLERANCE) {
            throw new IllegalArgumentException("Weights must sum to 1.0, got " + total);
        }
    }

    /** Whether this set still asks for a vector. */
    public boolean usesEmbedding() {
        return embedding > 0;
    }

    private static void requireFraction(double value, String name) {
        if (value < 0 || value > 1 || Double.isNaN(value)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, got " + value);
        }
    }
}
