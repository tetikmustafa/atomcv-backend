package com.mustafatetik.atomcv.generation.scoring;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything Faz B needs about one atom, and nothing else (Bolum 19.2).
 *
 * <p>A projection rather than the entity, so that the scorer is a pure
 * function of values: it can be called with a hand-written atom in a test, it
 * cannot lazily load anything mid-scoring, and the determinism Bolum 19.6
 * requires is a property of the arguments rather than of the session.
 *
 * <p>{@code tags} and {@code skills} arrive already canonical — lowercase,
 * English. Canonicalising here would mean the scorer lowercased text, and
 * absolute rule 7 is easier to keep in one place than in four.
 *
 * @param embedding  the stored vector, or null for an atom nothing has
 *                   embedded yet. Bolum 28.2 computes these after the fact, so
 *                   a freshly written atom is scoreable before it is embedded.
 * @param importance the user's own weighting, 0 to 1, which becomes
 *                   Bolum 19.1's multiplier
 */
public record ScorableAtom(
        UUID atomId,
        float[] embedding,
        Set<String> tags,
        Set<String> skills,
        List<String> titleTokens,
        double importance) {

    public ScorableAtom {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        skills = skills == null ? Set.of() : Set.copyOf(skills);
        titleTokens = titleTokens == null ? List.of() : List.copyOf(titleTokens);
        if (importance < 0 || importance > 1) {
            throw new IllegalArgumentException("importance is 0..1, got " + importance);
        }
        embedding = embedding == null ? null : embedding.clone();
    }

    /** Copied: the array is mutable and a scorer must not be able to change it. */
    @Override
    public float[] embedding() {
        return embedding == null ? null : embedding.clone();
    }

    public boolean hasEmbedding() {
        return embedding != null;
    }
}
