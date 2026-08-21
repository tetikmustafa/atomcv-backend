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
 */
@ConfigurationProperties(prefix = "atomcv.embedding")
public record EmbeddingProperties(
        String baseUrl, Duration requestTimeout, Duration healthTimeout) {

    public EmbeddingProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8081" : baseUrl;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
        healthTimeout = healthTimeout == null ? Duration.ofSeconds(2) : healthTimeout;
    }
}
