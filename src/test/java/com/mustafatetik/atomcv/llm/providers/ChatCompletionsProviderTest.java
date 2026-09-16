package com.mustafatetik.atomcv.llm.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
 * The two adapters that speak OpenAI's protocol, against a local server.
 *
 * <p>A real server rather than a mocked client, for the reason the OpenRouter
 * test gives: what is under test is the bytes that go on the wire and how each
 * status is read back, and a mock would assert on the code's own idea of both.
 *
 * <p>The two are tested together because the thing worth asserting is where
 * they <em>differ</em> — one sends a schema, the other cannot — and two
 * separate classes would state the shared half twice and let the difference
 * fall between them.
 */
class ChatCompletionsProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    record Analysis(String title, List<String> skills) {
    }

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<Integer> nextStatus = new AtomicReference<>(200);
    private final AtomicReference<String> nextBody = new AtomicReference<>("{}");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/chat/completions", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
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

    // ── No key is a silent skip ──────────────────────────────────────────

    @Test
    void anadapterWithNoKeyIsUnavailableRatherThanFailing() {
        assertThat(openAi("", "some-model").isAvailable()).isFalse();
        assertThat(deepSeek("", "some-model").isAvailable()).isFalse();
    }

    /**
     * A key with no model is as unusable as no key: the request would name an
     * empty model and come back 400 on every call. This is also what keeps the
     * three new adapters inert in a deployment that has not configured them —
     * the whole reason writing them changed nothing.
     */
    @Test
    void akeyWithNoModelIsAlsoUnavailable() {
        assertThat(openAi("sk-test", "").isAvailable()).isFalse();
        assertThat(openAi("sk-test", "some-model").isAvailable()).isTrue();
    }

    // ── Where the two differ, which is the point of the column ───

    @Test
    void openAiSendsTheSchemaForTheVendorToEnforce() {
        respond(200, envelope("""
                {"title":"Backend Engineer","skills":["java"]}"""));

        openAi("sk-test", "some-model").callStructured(request());

        JsonNode format = readLastBody().path("response_format");
        assertThat(format.path("type").asText()).isEqualTo("json_schema");
        // Faz A is held to 99%+ conformance; without strict the vendor reads
        // the schema as a suggestion.
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(format.path("json_schema").path("schema").path("type").asText())
                .isEqualTo("object");
    }

    /**
     * DeepSeek has no schema mode at all, so the shape has to be asked for in
     * words — and asked for in the <em>system</em> half. A schema is ours, and
     * the boundary is where the data starts, not which field looks structured.
     */
    @Test
    void deepSeekAsksForTheShapeInWordsAndOutsideTheFence() {
        respond(200, envelope("""
                {"title":"t","skills":[]}"""));

        deepSeek("sk-test", "some-model").callStructured(request());

        JsonNode body = readLastBody();
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");

        JsonNode messages = body.path("messages");
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).path("content").asText())
                .as("the schema travels with the instructions")
                .contains("\"type\":\"object\"");
        assertThat(messages.get(1).path("content").asText())
                .as("and never inside the user's half")
                .isEqualTo("a posting")
                .doesNotContain("type");
    }

    // ── The shared half ───────────────────────────────────────────────────

    @Test
    void thekeyTravelsAsABearerTokenAndNowhereElse() {
        respond(200, envelope("""
                {"title":"t","skills":[]}"""));

        openAi("sk-test", "some-model").callStructured(request());

        assertThat(lastAuth.get()).isEqualTo("Bearer sk-test");
        assertThat(readLastBody().toString()).doesNotContain("sk-test");
    }

    @Test
    void theanswerIsReadOutOfTheFirstChoice() {
        respond(200, envelope("""
                {"title":"Backend Engineer","skills":["java","go"]}"""));

        LlmOutcome<Analysis> outcome = openAi("sk-test", "some-model").callStructured(request());

        assertThat(outcome).isInstanceOf(LlmOutcome.Answered.class);
        Analysis answer = ((LlmOutcome.Answered<Analysis>) outcome).response().data();
        assertThat(answer.title()).isEqualTo("Backend Engineer");
        assertThat(answer.skills()).containsExactly("java", "go");
    }

    /**
     * <strong>The two spellings of the same discount.</strong> OpenAI reports
     * the cached prefix under {@code prompt_tokens_details.cached_tokens} and
     * DeepSeek reports it as {@code prompt_cache_hit_tokens}. An adapter
     * reading one would price a cached call at the other vendor as fresh —
     * quietly, and always in the expensive direction.
     */
    @Test
    void thecachedPrefixIsReadUnderEitherVendorsName() {
        respond(200, """
                {"choices":[{"message":{"content":"{\\"title\\":\\"t\\",\\"skills\\":[]}"}}],
                 "usage":{"prompt_tokens":1000,"completion_tokens":50,
                          "prompt_tokens_details":{"cached_tokens":800}}}""");
        assertThat(cachedTokensFrom(openAi("sk-test", "some-model"))).isEqualTo(800);

        respond(200, """
                {"choices":[{"message":{"content":"{\\"title\\":\\"t\\",\\"skills\\":[]}"}}],
                 "usage":{"prompt_tokens":1000,"completion_tokens":50,
                          "prompt_cache_hit_tokens":640}}""");
        assertThat(cachedTokensFrom(deepSeek("sk-test", "some-model"))).isEqualTo(640);
    }

    // ── Which status sends the chain where ───────────────────────────────

    @Test
    void arateLimitAdvancesTheChainAndABadRequestDoesNot() {
        respond(429, "{}");
        assertThat(kindFrom(openAi("sk-test", "some-model")))
                .isEqualTo(LlmFailure.Kind.RATE_LIMITED);

        respond(503, "{}");
        assertThat(kindFrom(openAi("sk-test", "some-model")))
                .isEqualTo(LlmFailure.Kind.SERVER_ERROR);

        respond(400, "{}");
        assertThat(kindFrom(openAi("sk-test", "some-model")))
                .as("another vendor would not fix a request this model cannot serve")
                .isEqualTo(LlmFailure.Kind.REQUEST_REJECTED);
    }

    /**
     * An answer that is valid JSON of the wrong shape is a schema mismatch,
     * which is retried in place rather than walking the chain. This is the
     * ordinary failure at DeepSeek, where the shape was only asked for.
     */
    @Test
    void anansweredCallWithNoContentIsASchemaMismatch() {
        respond(200, "{\"choices\":[]}");

        assertThat(kindFrom(deepSeek("sk-test", "some-model")))
                .isEqualTo(LlmFailure.Kind.SCHEMA_MISMATCH);
    }

    /** Absolute rule 4: nothing that came back travels with the failure. */
    @Test
    void afailureCarriesNoBodyAndNoPrompt() {
        respond(500, "{\"error\":\"context: a posting about Kubernetes\"}");

        LlmOutcome<Analysis> outcome = openAi("sk-test", "some-model").callStructured(request());
        LlmFailure failure = ((LlmOutcome.Failed<Analysis>) outcome).failure();

        assertThat(failure.detail()).isEqualTo("http 500");
        assertThat(failure.detail()).doesNotContain("Kubernetes");
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private OpenAiProvider openAi(String key, String model) {
        return new OpenAiProvider(new OpenAiProperties(key, baseUrl), llm("openai", model), JSON);
    }

    private DeepSeekProvider deepSeek(String key, String model) {
        return new DeepSeekProvider(
                new DeepSeekProperties(key, baseUrl), llm("deepseek", model), JSON);
    }

    private static LlmProperties llm(String id, String model) {
        return new LlmProperties(Map.of(ModelTier.CHEAP, List.of(id)),
                Map.of(id, model), 0);
    }

    private static StructuredRequest<Analysis> request() {
        return new StructuredRequest<>("job_analysis", "v1", "You analyse postings.", "a posting",
                new JsonSchema("job_analysis", JSON.createObjectNode().put("type", "object")),
                Analysis.class, ModelTier.CHEAP, Duration.ofSeconds(5));
    }

    private void respond(int status, String body) {
        nextStatus.set(status);
        nextBody.set(body);
    }

    private static String envelope(String content) {
        try {
            return JSON.createObjectNode()
                    .set("choices", JSON.createArrayNode().add(JSON.createObjectNode()
                            .set("message", JSON.createObjectNode().put("content", content))))
                    .toString();
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }

    private JsonNode readLastBody() {
        try {
            return JSON.readTree(lastBody.get());
        } catch (Exception malformed) {
            throw new AssertionError("the adapter sent something that is not JSON", malformed);
        }
    }

    private static int cachedTokensFrom(ChatCompletionsProvider provider) {
        LlmOutcome<Analysis> outcome = provider.callStructured(request());
        return ((LlmOutcome.Answered<Analysis>) outcome).response().cachedTokens();
    }

    private static LlmFailure.Kind kindFrom(ChatCompletionsProvider provider) {
        LlmOutcome<Analysis> outcome = provider.callStructured(request());
        return ((LlmOutcome.Failed<Analysis>) outcome).failure().kind();
    }
}
