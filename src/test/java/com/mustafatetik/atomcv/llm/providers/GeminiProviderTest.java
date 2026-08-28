package com.mustafatetik.atomcv.llm.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.gateway.JsonSchema;
import com.mustafatetik.atomcv.llm.gateway.LlmFailure;
import com.mustafatetik.atomcv.llm.gateway.LlmOutcome;
import com.mustafatetik.atomcv.llm.gateway.LlmProperties;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The second adapter, against a real socket (Bolum 27.2).
 *
 * <p>A local {@link HttpServer} rather than a mocked client, for the reason the
 * first adapter's test gives: what is being tested is the shape that goes on
 * the wire and the shape that comes back, and a mock would assert that the
 * adapter calls a method rather than that it speaks the vendor's protocol.
 */
class GeminiProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    record Analysis(String title, List<String> skills) {
    }

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastKey = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<Integer> nextStatus = new AtomicReference<>(200);
    private final AtomicReference<String> nextBody = new AtomicReference<>("{}");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/models", exchange -> {
            lastPath.set(exchange.getRequestURI().toString());
            lastKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            byte[] bytes = nextBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(nextStatus.get(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ── Bolum 27.3: no key is a silent skip ───────────────────────────────

    @Test
    void aProviderWithNoKeyIsUnavailableRatherThanFailing() {
        assertThat(provider("", "gemini-2.5-flash-lite").isAvailable()).isFalse();
    }

    @Test
    void aKeyWithNoModelIsAlsoUnavailable() {
        assertThat(provider("k", "").isAvailable()).isFalse();
        assertThat(provider("k", "gemini-2.5-flash-lite").isAvailable()).isTrue();
    }

    // ── the request ───────────────────────────────────────────────────────

    /**
     * The key goes in a header. Google's own examples put it in {@code ?key=},
     * which is the part of a request that proxies and access logs write down.
     */
    @Test
    void theKeyTravelsInAHeaderAndNotTheQueryString() {
        answerWith(candidate("{\"title\":\"Engineer\",\"skills\":[\"go\"]}"));

        provider("secret-key", "gemini-2.5-flash-lite").callStructured(request());

        assertThat(lastKey.get()).isEqualTo("secret-key");
        assertThat(lastPath.get())
                .isEqualTo("/models/gemini-2.5-flash-lite:generateContent")
                .doesNotContain("secret-key");
    }

    @Test
    void theSystemPromptIsItsOwnFieldRatherThanAFirstTurn() throws Exception {
        answerWith(candidate("{\"title\":\"Engineer\",\"skills\":[\"go\"]}"));

        provider("k", "m").callStructured(request());

        var body = JSON.readTree(lastBody.get());
        assertThat(body.path("systemInstruction").path("parts").path(0).path("text").asText())
                .isEqualTo("a system prompt");
        assertThat(body.path("contents").path(0).path("parts").path(0).path("text").asText())
                .isEqualTo("a posting");
    }

    /**
     * The narrowing GeminiSchema does, seen from outside. Every prompt schema
     * in this repository carries {@code additionalProperties}, and sending one
     * unchanged is a 400 on every call — the chain would have read that as the
     * vendor being down.
     */
    @Test
    void theSchemaOnTheWireCarriesNothingGeminiRefuses() throws Exception {
        answerWith(candidate("{\"title\":\"Engineer\",\"skills\":[\"go\"]}"));

        provider("k", "m").callStructured(request());

        String schema = JSON.readTree(lastBody.get())
                .path("generationConfig").path("responseSchema").toString();
        assertThat(schema)
                .doesNotContain("additionalProperties")
                .doesNotContain("minimum")
                .doesNotContain("maximum")
                .contains("\"title\"")
                .contains("\"required\"");
    }

    // ── the answer ────────────────────────────────────────────────────────

    @Test
    void anAnswerIsParsedWithTheTokensItCost() {
        answerWith("""
                {"candidates":[{"content":{"parts":[
                   {"text":"{\\"title\\":\\"Engineer\\",\\"skills\\":[\\"go\\",\\"sql\\"]}"}]}}],
                 "usageMetadata":{"promptTokenCount":120,"candidatesTokenCount":30,
                                  "cachedContentTokenCount":80}}
                """);

        var response = answered(provider("k", "gemini-2.5-flash-lite")
                .callStructured(request()));

        assertThat(response.data()).isEqualTo(new Analysis("Engineer", List.of("go", "sql")));
        assertThat(response.provider()).isEqualTo("gemini");
        assertThat(response.inputTokens()).isEqualTo(120);
        assertThat(response.outputTokens()).isEqualTo(30);
        // A cached token is a discounted subset of the input, never an
        // addition (Bolum 27.4).
        assertThat(response.cachedTokens()).isEqualTo(80);
        assertThat(response.billedInputTokens()).isEqualTo(40);
    }

    /** A model that reports no usage costs zero here, not an invented number. */
    @Test
    void anAnswerWithNoUsageBlockCostsZeroRatherThanAGuess() {
        answerWith(candidate("{\"title\":\"Engineer\",\"skills\":[]}"));

        var response = answered(provider("k", "m").callStructured(request()));

        assertThat(response.inputTokens()).isZero();
        assertThat(response.outputTokens()).isZero();
    }

    /**
     * A safety block comes back as 200 with a finishReason and no parts. The
     * kind is the same as any other non-answer: this vendor did not answer, and
     * the next one may.
     */
    @Test
    void aBlockedCandidateIsAFailureRatherThanAnException() {
        answerWith("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}");

        var failure = failed(provider("k", "m").callStructured(request()));

        assertThat(failure.kind()).isEqualTo(LlmFailure.Kind.SCHEMA_MISMATCH);
        assertThat(failure.provider()).isEqualTo("gemini");
    }

    // ── Bolum 27.3's routing ──────────────────────────────────────────────

    @Test
    void aRateLimitIsAReasonToAskSomebodyElse() {
        nextStatus.set(429);
        nextBody.set("{}");

        var failure = failed(provider("k", "m").callStructured(request()));

        assertThat(failure.kind()).isEqualTo(LlmFailure.Kind.RATE_LIMITED);
        assertThat(failure.kind().tryNextProvider()).isTrue();
    }

    @Test
    void aServerErrorIsToo() {
        nextStatus.set(503);
        nextBody.set("{}");

        assertThat(failed(provider("k", "m").callStructured(request())).kind())
                .isEqualTo(LlmFailure.Kind.SERVER_ERROR);
    }

    /**
     * A 400 is usually a schema this vendor will not take. Asking it again buys
     * the same refusal, but another vendor may well accept it — so it stays a
     * reason to move on.
     */
    @Test
    void aRefusedRequestStillLetsTheChainWalkOn() {
        nextStatus.set(400);
        nextBody.set("{}");

        var failure = failed(provider("k", "m").callStructured(request()));

        assertThat(failure.kind()).isEqualTo(LlmFailure.Kind.REQUEST_REJECTED);
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private void answerWith(String body) {
        nextStatus.set(200);
        nextBody.set(body);
    }

    private static String candidate(String json) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + JSON.valueToTree(json).toString() + "}]}}]}";
    }

    private GeminiProvider provider(String key, String model) {
        return new GeminiProvider(
                new GeminiProperties(key, baseUrl),
                new LlmProperties(Map.of(ModelTier.CHEAP, List.of("gemini")),
                        Map.of("gemini", model), Duration.ofSeconds(5), 0),
                JSON);
    }

    /** Carries every keyword the real prompt schemas use, including the three dropped. */
    private static StructuredRequest<Analysis> request() {
        try {
            var schema = JSON.readTree("""
                    {"type":"object","additionalProperties":false,
                     "required":["title","skills"],
                     "properties":{
                       "title":{"type":"string"},
                       "confidence":{"type":"number","minimum":0,"maximum":1},
                       "skills":{"type":"array","items":{"type":"string"}}}}
                    """);
            return new StructuredRequest<>("job_analysis", "v1", "a system prompt", "a posting",
                    new JsonSchema("job_analysis", schema), Analysis.class,
                    ModelTier.CHEAP, Duration.ofSeconds(5));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static com.mustafatetik.atomcv.llm.gateway.LlmResponse<Analysis> answered(
            LlmOutcome<Analysis> outcome) {
        return ((LlmOutcome.Answered<Analysis>) outcome).response();
    }

    private static LlmFailure failed(LlmOutcome<Analysis> outcome) {
        return ((LlmOutcome.Failed<Analysis>) outcome).failure();
    }
}
