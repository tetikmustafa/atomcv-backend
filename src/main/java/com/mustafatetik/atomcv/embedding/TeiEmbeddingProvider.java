package com.mustafatetik.atomcv.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * BGE-M3 behind HuggingFace's text-embeddings-inference (Bolum 28.1).
 *
 * <p>Self-hosted rather than an API: the text is the user's own CV, and
 * Bolum 28.1's first reason for the whole arrangement is that it never leaves.
 * That also means this client is the only thing that talks to the container.
 *
 * <p><strong>Nothing here logs the text.</strong> It is atom content — absolute
 * rule 4. The log lines carry counts and statuses.
 */
@Component
@Profile("!local-fake")
public class TeiEmbeddingProvider implements EmbeddingProvider {

    /** BGE-M3's dense output (Bolum 28.1). */
    static final int DIMENSIONS = 1024;

    private static final Logger log = LoggerFactory.getLogger(TeiEmbeddingProvider.class);

    private final HttpClient http;
    private final EmbeddingProperties properties;
    private final ObjectMapper json;

    public TeiEmbeddingProvider(EmbeddingProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /**
     * TEI's own {@code /health}, which answers only once the weights are
     * loaded — the port opens well before that, so a connection test would
     * report healthy through the whole of a 2.5 GB first start.
     */
    @Override
    public boolean isHealthy() {
        try {
            var response = http.send(HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl() + "/health"))
                    .timeout(properties.healthTimeout())
                    .GET().build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception unreachable) {
            return false;
        }
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        var body = json.createObjectNode();
        body.set("inputs", json.valueToTree(texts));
        // TEI truncates rather than refusing: a long bullet should embed its
        // beginning, not fail the batch it happened to be in.
        body.put("truncate", true);

        var response = send(body.toString());
        var vectors = parse(response, texts.size());
        log.debug("Embedded {} texts", texts.size());
        return vectors;
    }

    private String send(String requestBody) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + "/embed"))
                .timeout(properties.requestTimeout())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // The status, never the body: TEI echoes the input in its
                // error payloads.
                throw new EmbeddingException(
                        "the embedding service answered " + response.statusCode(), null);
            }
            return response.body();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("interrupted while embedding", interrupted);
        } catch (IOException unreachable) {
            throw new EmbeddingException("the embedding service could not be reached",
                    unreachable);
        }
    }

    private List<float[]> parse(String body, int expected) {
        try {
            var root = json.readTree(body);
            if (!root.isArray() || root.size() != expected) {
                // A short answer would silently pair the wrong vector with the
                // wrong atom, which is worse than no vector at all.
                throw new EmbeddingException(
                        "asked for " + expected + " vectors, got " + root.size(), null);
            }
            var vectors = new java.util.ArrayList<float[]>(expected);
            for (var node : root) {
                if (node.size() != DIMENSIONS) {
                    throw new EmbeddingException(
                            "the model returned " + node.size() + " dimensions, not " + DIMENSIONS,
                            null);
                }
                var vector = new float[DIMENSIONS];
                for (int index = 0; index < DIMENSIONS; index++) {
                    vector[index] = (float) node.get(index).asDouble();
                }
                vectors.add(vector);
            }
            return List.copyOf(vectors);
        } catch (IOException malformed) {
            throw new EmbeddingException("the embedding service answered something else",
                    malformed);
        }
    }
}
