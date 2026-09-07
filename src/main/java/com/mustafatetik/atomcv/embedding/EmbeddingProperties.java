package com.mustafatetik.atomcv.embedding;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the embedding service is and how long it is given (Bolum 28).
 *
 * @param baseUrl        the TEI container. In development the port is
 *                       published; in production it is a name on the isolated
 *                       network, the same arrangement the compiler has.
 * @param requestTimeout per call. CPU inference on a batch is slower than a
 *                       web request has any right to be, and the work runs on
 *                       a queue rather than in front of a user (Bolum 28.2).
 * @param healthTimeout  short: a health check that waits as long as a real
 *                       call would defeats the point of asking.
 * @param batchSize      how many texts go in one request.
 *
 *                       <p>Thirty-two because that is TEI's own default for
 *                       {@code --max-client-batch-size}, so the client works
 *                       against a server nobody has tuned. It was not a setting
 *                       at all before, and the whole profile went in one call:
 *                       an 84-atom import came back {@code 413 batch size 84 >
 *                       maximum allowed batch size 32} and every profile larger
 *                       than 32 atoms had never embedded against a real TEI —
 *                       the 28-atom one in front of us fitted underneath and
 *                       hid it.
 */
@ConfigurationProperties(prefix = "atomcv.embedding")
public record EmbeddingProperties(
        String baseUrl, Duration requestTimeout, Duration healthTimeout, Integer batchSize) {

    public EmbeddingProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8081" : baseUrl;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
        healthTimeout = healthTimeout == null ? Duration.ofSeconds(2) : healthTimeout;
        batchSize = batchSize == null ? 32 : batchSize;
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize is at least 1, was " + batchSize);
        }
    }
}
