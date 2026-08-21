package com.mustafatetik.atomcv.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The TEI client against a local server. A real server rather than a mocked
 * client, because what is under test is the request that goes on the wire and
 * how each answer is read.
 */
class TeiEmbeddingProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<int[]> embedStatus = new AtomicReference<>(new int[] {200});
    private final AtomicReference<String> embedBody = new AtomicReference<>("[]");
    private final AtomicReference<int[]> healthStatus = new AtomicReference<>(new int[] {200});

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/embed", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, embedStatus.get()[0], embedBody.get());
        });
        server.createContext("/health", exchange -> respond(exchange, healthStatus.get()[0], ""));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ── The request ──────────────────────────────────────────────────────

    @Test
    void abatchTravelsAsOneRoundTrip() throws Exception {
        embedBody.set(vectors(3));

        var result = provider().embedBatch(List.of("one", "two", "three"));

        assertThat(result).hasSize(3);
        var sent = JSON.readTree(lastBody.get());
        assertThat(sent.path("inputs")).hasSize(3);
        assertThat(sent.path("inputs").get(0).asText()).isEqualTo("one");
    }

    /**
     * A long bullet should embed its beginning rather than fail the batch it
     * happened to be in.
     */
    @Test
    void theServiceIsAskedToTruncateRatherThanRefuse() throws Exception {
        embedBody.set(vectors(1));

        provider().embed("a bullet");

        assertThat(JSON.readTree(lastBody.get()).path("truncate").asBoolean()).isTrue();
    }

    @Test
    void anEmptyBatchIsNotARoundTrip() {
        assertThat(provider().embedBatch(List.of())).isEmpty();
        assertThat(lastBody.get()).isNull();
    }

    // ── Reading the answer ───────────────────────────────────────────────

    @Test
    void theVectorComesBackAtTheDeclaredDimension() {
        embedBody.set(vectors(1));

        assertThat(provider().embed("a bullet")).hasSize(1024);
    }

    /**
     * A short answer would pair the wrong vector with the wrong atom, which is
     * worse than no vector at all — the profile would score against someone
     * else's bullet and nothing would look broken.
     */
    @Test
    void ananswerWithFewerVectorsThanInputsIsRefused() {
        embedBody.set(vectors(2));

        assertThatThrownBy(() -> provider().embedBatch(List.of("one", "two", "three")))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("asked for 3");
    }

    /** A different model behind the same port would silently break the column. */
    @Test
    void ananswerAtTheWrongDimensionIsRefused() {
        embedBody.set("[[" + "0.1,".repeat(767) + "0.1]]");

        assertThatThrownBy(() -> provider().embed("a bullet"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("768");
    }

    @Test
    void anErrorStatusIsRefusedWithoutEchoingTheInput() {
        embedStatus.set(new int[] {413});
        embedBody.set("{\"error\":\"input too long: a bullet about payments\"}");

        assertThatThrownBy(() -> provider().embed("a bullet about payments"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("413")
                // Absolute rule 4: the message is read into a log line, and
                // TEI echoes the input in its error payloads.
                .hasMessageNotContaining("payments");
    }

    @Test
    void anUnreachableServiceIsRefused() {
        server.stop(0);

        assertThatThrownBy(() -> provider().embed("a bullet"))
                .isInstanceOf(EmbeddingException.class);
    }

    // ── Health (Bolum 28.4) ──────────────────────────────────────────────

    @Test
    void healthFollowsTheServicesOwnEndpoint() {
        assertThat(provider().isHealthy()).isTrue();

        healthStatus.set(new int[] {503});
        assertThat(provider().isHealthy()).isFalse();
    }

    /**
     * TEI opens its port well before the weights are loaded. A health check
     * that only proved something was listening would report healthy through
     * the whole of a 2.5 GB first start, and scoring would run against a
     * service that answers 503 to every call.
     */
    @Test
    void anAbsentServiceIsUnhealthyRatherThanThrowing() {
        server.stop(0);

        assertThat(provider().isHealthy()).isFalse();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private TeiEmbeddingProvider provider() {
        return new TeiEmbeddingProvider(new EmbeddingProperties(
                baseUrl, Duration.ofSeconds(5), Duration.ofSeconds(1)), JSON);
    }

    private static String vectors(int count) {
        var one = "[" + IntStream.range(0, 1024).mapToObj(index -> "0.01")
                .collect(Collectors.joining(",")) + "]";
        return "[" + IntStream.range(0, count).mapToObj(index -> one)
                .collect(Collectors.joining(",")) + "]";
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
                                int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
