package com.mustafatetik.atomcv.embedding;

import java.util.List;

/**
 * Text as a vector (Bolum 28.3).
 *
 * <p>Ports and adapters: BGE-M3 is self-hosted today because the content is
 * the user's own CV and Bolum 28.1 keeps it inside, but the day that changes
 * it should be one adapter and nothing else.
 *
 * <p>Every vector is computed from the <strong>English</strong> variant
 * (Bolum 28.2). A multilingual model still places a Turkish sentence and its
 * English translation apart, so a similarity between a Turkish bullet and an
 * English posting would measure the languages as much as the match.
 */
public interface EmbeddingProvider {

    /** How long a vector this provider returns. The column is {@code vector(1024)}. */
    int dimensions();

    /**
     * Whether the service is answering.
     *
     * <p>Bolum 28.4 reads this to decide whether scoring runs with its
     * embedding component or without it: quality drops, the product keeps
     * working. It is a health signal, not a promise — a provider that was
     * healthy a moment ago can still fail the next call.
     */
    boolean isHealthy();

    float[] embed(String text);

    /**
     * One round trip for many texts.
     *
     * <p>Separate from {@link #embed} rather than a loop over it because it is
     * the shape the work actually has: a profile is embedded atom by atom
     * after an import, and doing that one HTTP request at a time is the
     * difference between a second and a minute.
     *
     * @return one vector per input, in the same order
     */
    List<float[]> embedBatch(List<String> texts);
}
