package com.mustafatetik.atomcv.generation.rewrite;

/**
 * What Faz D is allowed to do to one bullet (Bolum 21.2).
 *
 * <p>Three tiers, and the third is not in this enum: an atom below the floor
 * is not a candidate at all. That is the point of Bolum 21.2 — where there is
 * no real connection between the sentence and the posting, adapting it is not
 * adaptation, it is invention.
 */
public enum RewriteIntent {

    /**
     * Score at or above {@code 0.65}: the connection is real, so drawing it
     * out is honest. Keywords may be integrated and terminology aligned.
     */
    ADAPT,

    /**
     * Between {@code 0.40} and {@code 0.65}: related, but not worth forcing.
     * The sentence may be said in fewer words and in no other way.
     */
    COMPRESS
}
