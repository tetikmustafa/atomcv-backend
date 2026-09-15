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
 * The Anthropic row, and the reason that table has a mechanism column.
 *
 * <p>Every other adapter asks for JSON and reads a string. This one defines a
 * tool, forces the model to call it, and reads the call's arguments — so the
 * assertions here are about a request and a response that look nothing like
 * the others', which is exactly what "ayri kod yolu" was warning about.
 */
class AnthropicProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    record Analysis(String title, List<String> skills) {
    }

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
    private final AtomicReference<String> lastVersionHeader = new AtomicReference<>();
    private final AtomicReference<Integer> nextStatus = new AtomicReference<>(200);
    private final AtomicReference<String> nextBody = new AtomicReference<>("{}");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/messages", exchange -> {
            var headers = exchange.getRequestHeaders();
            lastKeyHeader.set(headers.getFirst("x-api-key"));
            lastAuthHeader.set(headers.getFirst("Authorization"));
            lastVersionHeader.set(headers.getFirst("anthropic-version"));
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

    @Test
    void anadapterWithNoKeyIsUnavailableRatherThanFailing() {
        assertThat(provider("", "some-model").isAvailable()).isFalse();
        assertThat(provider("sk-ant", "").isAvailable()).isFalse();
        assertThat(provider("sk-ant", "some-model").isAvailable()).isTrue();
    }

    /**
     * <strong>The key is not a Bearer token here.</strong> This API takes it in
     * a header of its own and rejects the Authorization form — an adapter
     * copied from the OpenAI shape would authenticate on no call at all, and
     * the failure would read as a bad key rather than as a wrong header.
     */
    @Test
    void thekeyTravelsInAnthropicsOwnHeaderAndNowhereElse() {
        respond(200, toolUse("""
                {"title":"t","skills":[]}"""));

        provider("sk-ant", "some-model").callStructured(request());

        assertThat(lastKeyHeader.get()).isEqualTo("sk-ant");
        assertThat(lastAuthHeader.get()).isNull();
        assertThat(lastVersionHeader.get()).isEqualTo("2023-06-01");
        assertThat(lastBody.get()).doesNotContain("sk-ant");
    }

    /**
     * The schema becomes a tool, and {@code tool_choice} names it. The name has
     * to match on both — the API refuses a request that forces a tool it was
     * not given.
     */
    @Test
    void theschemaIsSentAsTheOnlyToolAndTheModelIsMadeToCallIt() {
        respond(200, toolUse("""
                {"title":"t","skills":[]}"""));

        provider("sk-ant", "some-model").callStructured(request());

        JsonNode body = readLastBody();
        assertThat(body.path("tools")).hasSize(1);
        assertThat(body.path("tools").get(0).path("name").asText()).isEqualTo("job_analysis");
        assertThat(body.path("tools").get(0).path("input_schema").path("type").asText())
                .isEqualTo("object");
        assertThat(body.path("tool_choice").path("type").asText()).isEqualTo("tool");
        assertThat(body.path("tool_choice").path("name").asText())
                .as("a forced tool the request did not define is a 400")
                .isEqualTo(body.path("tools").get(0).path("name").asText());
        assertThat(body.path("max_tokens").asInt())
                .as("required by this API and defaulted by every other")
                .isPositive();
    }

    /**
     * The system prompt is a top-level field, not a message with a role.
     * Instructions and data are separated on purpose; here the API does it
     * structurally, and sending the system half as a message would put the
     * instructions inside the fence.
     */
    @Test
    void thesystemPromptIsAFieldRatherThanAMessage() {
        respond(200, toolUse("""
                {"title":"t","skills":[]}"""));

        provider("sk-ant", "some-model").callStructured(request());

        JsonNode body = readLastBody();
        assertThat(body.path("system").asText()).isEqualTo("You analyse postings.");
        assertThat(body.path("messages")).hasSize(1);
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo("a posting");
    }

    @Test
    void theanswerIsReadOutOfTheToolCallsArguments() {
        respond(200, toolUse("""
                {"title":"Backend Engineer","skills":["java","go"]}"""));

        LlmOutcome<Analysis> outcome = provider("sk-ant", "some-model").callStructured(request());

        assertThat(outcome).isInstanceOf(LlmOutcome.Answered.class);
        Analysis answer = ((LlmOutcome.Answered<Analysis>) outcome).response().data();
        assertThat(answer.title()).isEqualTo("Backend Engineer");
        assertThat(answer.skills()).containsExactly("java", "go");
    }

    /**
     * <strong>The block is found, not indexed at zero.</strong> A model may
     * emit a text block before the tool call — thinking out loud before
     * answering — and an adapter reading position zero would turn a perfectly
     * good answer into a schema mismatch, on the model's whim, in production.
     */
    @Test
    void atextBlockBeforeTheToolCallDoesNotLoseTheAnswer() {
        respond(200, """
                {"content":[
                   {"type":"text","text":"Let me work through this posting."},
                   {"type":"tool_use","name":"job_analysis",
                    "input":{"title":"Backend Engineer","skills":["go"]}}],
                 "usage":{"input_tokens":10,"output_tokens":5}}""");

        LlmOutcome<Analysis> outcome = provider("sk-ant", "some-model").callStructured(request());

        assertThat(((LlmOutcome.Answered<Analysis>) outcome).response().data().title())
                .isEqualTo("Backend Engineer");
    }

    /**
     * A model that answered in prose rather than calling the tool is a schema
     * mismatch — retried in place, not walked past to a vendor that would do
     * the same thing.
     */
    @Test
    void ananswerWithNoToolCallIsASchemaMismatch() {
        respond(200, """
                {"content":[{"type":"text","text":"I cannot answer that."}],
                 "usage":{"input_tokens":10,"output_tokens":5}}""");

        assertThat(kindFrom(provider("sk-ant", "some-model")))
                .isEqualTo(LlmFailure.Kind.SCHEMA_MISMATCH);
    }

    /** The cached prefix is the read half, and it is discounted. */
    @Test
    void thecachedPrefixIsReadFromTheCacheReadCount() {
        respond(200, """
                {"content":[{"type":"tool_use","name":"job_analysis",
                             "input":{"title":"t","skills":[]}}],
                 "usage":{"input_tokens":1000,"output_tokens":50,
                          "cache_read_input_tokens":900}}""");

        LlmOutcome<Analysis> outcome = provider("sk-ant", "some-model").callStructured(request());

        assertThat(((LlmOutcome.Answered<Analysis>) outcome).response().cachedTokens())
                .isEqualTo(900);
    }

    @Test
    void arateLimitAdvancesTheChainAndABadRequestDoesNot() {
        respond(429, "{}");
        assertThat(kindFrom(provider("sk-ant", "some-model")))
                .isEqualTo(LlmFailure.Kind.RATE_LIMITED);

        respond(400, "{}");
        assertThat(kindFrom(provider("sk-ant", "some-model")))
                .isEqualTo(LlmFailure.Kind.REQUEST_REJECTED);
    }

    /** Absolute rule 4: nothing that came back travels with the failure. */
    @Test
    void afailureCarriesNoBodyAndNoPrompt() {
        respond(500, "{\"error\":{\"message\":\"a posting about Kubernetes\"}}");

        LlmOutcome<Analysis> outcome = provider("sk-ant", "some-model").callStructured(request());

        assertThat(((LlmOutcome.Failed<Analysis>) outcome).failure().detail())
                .isEqualTo("http 500")
                .doesNotContain("Kubernetes");
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private AnthropicProvider provider(String key, String model) {
        return new AnthropicProvider(
                new AnthropicProperties(key, baseUrl, null, 0),
                new LlmProperties(Map.of(ModelTier.MID, List.of("anthropic")),
                        Map.of("anthropic", model), Duration.ofSeconds(5), 0),
                JSON);
    }

    private static StructuredRequest<Analysis> request() {
        return new StructuredRequest<>("job_analysis", "v1", "You analyse postings.", "a posting",
                new JsonSchema("job_analysis", JSON.createObjectNode().put("type", "object")),
                Analysis.class, ModelTier.MID, Duration.ofSeconds(5));
    }

    private void respond(int status, String body) {
        nextStatus.set(status);
        nextBody.set(body);
    }

    private static String toolUse(String input) {
        return """
                {"content":[{"type":"tool_use","name":"job_analysis","input":%s}],
                 "usage":{"input_tokens":10,"output_tokens":5}}""".formatted(input);
    }

    private JsonNode readLastBody() {
        try {
            return JSON.readTree(lastBody.get());
        } catch (Exception malformed) {
            throw new AssertionError("the adapter sent something that is not JSON", malformed);
        }
    }

    private static LlmFailure.Kind kindFrom(AnthropicProvider provider) {
        LlmOutcome<Analysis> outcome = provider.callStructured(request());
        return ((LlmOutcome.Failed<Analysis>) outcome).failure().kind();
    }
}
