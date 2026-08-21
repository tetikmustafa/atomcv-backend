package com.mustafatetik.atomcv.embedding;

/**
 * The embedding service did not answer, or answered something unusable.
 *
 * <p>An exception rather than a {@code Result}, unlike the LLM gateway next to
 * it, because the two failures are not the same shape. A provider that will
 * not answer is a decision the chain has to make; an embedding that failed is
 * something the caller either retries on the queue (Bolum 28.2) or scores
 * without (Bolum 28.4), and neither of those reads better as a return value
 * threaded through a batch loop.
 *
 * <p>{@code EMBEDDING_UNAVAILABLE} is the catalogue code it becomes if it ever
 * reaches a user, which Bolum 28.4 says it should not: scoring drops the
 * embedding component and carries on.
 */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
