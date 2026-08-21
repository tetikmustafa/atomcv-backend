package com.mustafatetik.atomcv.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Vectors from a hash, so the pipeline runs without the 2.5 GB container
 * (Bolum 54.2).
 *
 * <p>Deterministic, which is the point: the same text is the same vector every
 * run, so a selection test that failed did so for a reason rather than by
 * draw.
 *
 * <p><strong>It is not a model.</strong> Two texts about the same subject are
 * no closer here than two unrelated ones, so nothing about <em>ranking</em>
 * can be asserted against it — that belongs against the real service or the
 * golden set. What it does support is every layer above: that a vector of the
 * right size is stored, invalidated, and read back.
 *
 * <p>One concession to usefulness: the seed is built from the text's words as
 * a set rather than from the string, so reordering a sentence keeps its vector
 * and Bolum 28.2's {@code content_hash} invalidation can be exercised on a
 * change that is genuinely a change.
 */
@Component
@Profile("local-fake")
public class FakeEmbeddingProvider implements EmbeddingProvider {

    /** BGE-M3's dense output, so the fake fits the same column (Bolum 28.1). */
    static final int DIMENSIONS = 1024;

    private static final Pattern WORDS = Pattern.compile("[^\\p{L}\\p{N}]+");

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /** Always: there is nothing to be unhealthy about. */
    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public float[] embed(String text) {
        var random = new Random(seedOf(text));
        var vector = new float[DIMENSIONS];
        double sumOfSquares = 0;
        for (int index = 0; index < DIMENSIONS; index++) {
            vector[index] = (float) random.nextGaussian();
            sumOfSquares += (double) vector[index] * vector[index];
        }
        // Unit length, because cosine similarity is what Bolum 19 computes and
        // a fake that returned unnormalised vectors would let a bug in that
        // normalisation pass unnoticed.
        var norm = (float) Math.sqrt(sumOfSquares);
        for (int index = 0; index < DIMENSIONS; index++) {
            vector[index] /= norm;
        }
        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    /**
     * The text's distinct words, lowercased and sorted, hashed.
     *
     * <p>{@code Locale.ROOT}: absolute rule 7. Under a Turkish default locale
     * the same text would seed differently on this machine and on the runner,
     * and a golden fixture recorded here would not reproduce in CI.
     */
    private static long seedOf(String text) {
        var normalized = String.join(" ", WORDS.splitAsStream(text.trim())
                .filter(word -> !word.isEmpty())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList());
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int index = 0; index < Long.BYTES; index++) {
                seed = (seed << 8) | (digest[index] & 0xFF);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
