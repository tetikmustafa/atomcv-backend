package com.mustafatetik.atomcv.llm.providers;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
 * Bolum 27.2's OpenRouter adapter, against a local server.
 *
 * <p>A real server rather than a mocked client: what is under test is the
 * request that goes on the wire and how each status is read, and a mock would
 * assert on the code's own idea of both.
 */
class OpenRouterProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    record Analysis(String title, List<String> skills) {
    }

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();

    private final AtomicReference<int[]> nextStatus = new AtomicReference<>(new int[] {200});
    private final AtomicReference<String> nextBody = new AtomicReference<>("{}");

    /**
     * F-014 is about what reaches the log, so the log is what the assertion
     * reads. An appender on the adapter's own logger rather than on the root:
     * anything else here would be someone else's line.
     */
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private ch.qos.logback.classic.Logger providerLog;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        // One context for the life of the test: registering a second one for
        // the same path throws, and several of these assert on more than one
        // status in a row.
        server.createContext("/chat/completions", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            byte[] bytes = nextBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(nextStatus.get()[0], bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        providerLog = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(OpenRouterProvider.class);
        logged.start();
        providerLog.addAppender(logged);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        providerLog.detachAppender(logged);
        logged.stop();
    }

    // ── Bolum 27.3: no key is a silent skip ───────────────────────────────

    @Test
    void aProviderWithNoKeyIsUnavailableRatherThanFailing() {
        assertThat(provider("", "some-model").isAvailable()).isFalse();
    }

    /**
     * A key with no model is as unusable as no key: the request would name an
     * empty model and come back 400 on every call.
     */
    @Test
    void aKeyWithNoModelIsAlsoUnavailable() {
        assertThat(provider("sk-test", "").isAvailable()).isFalse();
        assertThat(provider("sk-test", "some-model").isAvailable()).isTrue();
    }

    // ── The request that goes on the wire ─────────────────────────────────

    @Test
    void theSchemaIsSentForTheProviderToEnforce() {
        respond(200, answerEnvelope("""
                {"title":"Backend Engineer","skills":["java"]}"""));

        provider("sk-test", "some-model").callStructured(request());

        var body = readLastBody();
        assertThat(body.path("model").asText()).isEqualTo("some-model");
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_schema");
        // Bolum 53.5 wants 99%+ schema conformance on Faz A. Without strict
        // the provider treats the schema as a suggestion.
        assertThat(body.path("response_format").path("json_schema").path("strict").asBoolean())
                .isTrue();
        assertThat(body.path("response_format").path("json_schema").path("schema")
                .path("type").asText()).isEqualTo("object");
    }

    @Test
    void theKeyTravelsAsABearerTokenAndNowhereElse() {
        respond(200, answerEnvelope("""
                {"title":"t","skills":[]}"""));

        provider("sk-test", "some-model").callStructured(request());

        assertThat(lastAuth.get()).isEqualTo("Bearer sk-test");
        assertThat(readLastBody().toString()).doesNotContain("sk-test");
    }

    @Test
    void theSystemPromptIsSentSeparatelySoItCanBeCached() {
        respond(200, answerEnvelope("""
                {"title":"t","skills":[]}"""));

        provider("sk-test", "some-model").callStructured(request());

        var messages = readLastBody().path("messages");
        // Bolum 27.4: the discount applies to a constant prefix, which it can
        // only be if it is its own message.
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(1).path("content").asText()).isEqualTo("a posting");
    }

    /**
     * Bolum 27.2's weaker mode. The provider promises valid JSON and nothing
     * about its shape, so the shape has to be asked for in words.
     */
    @Test
    void theJsonObjectModeCarriesTheSchemaInTheSystemPromptInstead() {
        respond(200, answerEnvelope("""
                {"title":"t","skills":[]}"""));

        provider("sk-test", "some-model",
                OpenRouterProperties.StructuredOutput.JSON_OBJECT).callStructured(request());

        var body = readLastBody();
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.path("response_format").has("json_schema")).isFalse();
        assertThat(body.path("messages").get(0).path("content").asText())
                .contains("\"type\":\"object\"");
    }

    // ── Reading the answer ────────────────────────────────────────────────

    @Test
    void theAnswerIsParsedFromTheMessageContent() {
        respond(200, answerEnvelope("""
                {"title":"Senior Backend Engineer","skills":["java","postgres"]}"""));

        var response = answered(provider("sk-test", "some-model").callStructured(request()));

        assertThat(response.data().title()).isEqualTo("Senior Backend Engineer");
        assertThat(response.data().skills()).containsExactly("java", "postgres");
        assertThat(response.provider()).isEqualTo("openrouter");
        assertThat(response.model()).isEqualTo("some-model");
    }

    /**
     * Bolum 27.4 prices a cached input token at a fraction of a fresh one, so
     * a cost computed without the split overstates every call a constant
     * system prompt made cheap.
     */
    @Test
    void theCachedPrefixIsReadApartFromFreshInput() {
        respond(200, """
                {"choices":[{"message":{"content":"{\\"title\\":\\"t\\",\\"skills\\":[]}"}}],
                 "usage":{"prompt_tokens":1200,"completion_tokens":300,
                          "prompt_tokens_details":{"cached_tokens":1000}}}""");

        var response = answered(provider("sk-test", "some-model").callStructured(request()));

        assertThat(response.inputTokens()).isEqualTo(1200);
        assertThat(response.cachedTokens()).isEqualTo(1000);
        assertThat(response.billedInputTokens()).isEqualTo(200);
        assertThat(response.outputTokens()).isEqualTo(300);
    }

    @Test
    void aProviderThatReportsNoCachingCountsNoneRatherThanGuessing() {
        respond(200, answerEnvelope("""
                {"title":"t","skills":[]}"""));

        assertThat(answered(provider("sk-test", "some-model").callStructured(request()))
                .cachedTokens()).isZero();
    }

    // ── Bolum 27.3: which status routes where ─────────────────────────────

    @Test
    void aRateLimitAdvancesTheChain() {
        assertThat(failure(429).kind()).isEqualTo(LlmFailure.Kind.RATE_LIMITED);
        assertThat(failure(429).kind().tryNextProvider()).isTrue();
    }

    @Test
    void aServerErrorAdvancesTheChain() {
        assertThat(failure(500).kind()).isEqualTo(LlmFailure.Kind.SERVER_ERROR);
        assertThat(failure(503).kind()).isEqualTo(LlmFailure.Kind.SERVER_ERROR);
    }

    /** A proxy in front of the vendor can answer these where the client saw no timeout. */
    @Test
    void aGatewayTimeoutIsATimeoutRatherThanAServerError() {
        assertThat(failure(504).kind()).isEqualTo(LlmFailure.Kind.TIMEOUT);
        assertThat(failure(408).kind()).isEqualTo(LlmFailure.Kind.TIMEOUT);
    }

    /**
     * A bad key or a model that does not exist is not fixed by asking someone
     * else, so it must not advance the chain.
     */
    @Test
    void aRejectedRequestDoesNotAdvanceTheChain() {
        assertThat(failure(401).kind()).isEqualTo(LlmFailure.Kind.REQUEST_REJECTED);
        assertThat(failure(404).kind()).isEqualTo(LlmFailure.Kind.REQUEST_REJECTED);
        assertThat(failure(400).kind().tryNextProvider()).isFalse();
    }

    // ── Answers that do not fit ───────────────────────────────────────────

    @Test
    void anAnswerThatIsNotJsonIsASchemaMismatch() {
        respond(200, """
                {"choices":[{"message":{"content":"I am sorry, I cannot do that."}}]}""");

        assertThat(failedOutcome(provider("sk-test", "some-model").callStructured(request()))
                .kind()).isEqualTo(LlmFailure.Kind.SCHEMA_MISMATCH);
    }

    @Test
    void anEnvelopeWithNoContentIsASchemaMismatchRatherThanACrash() {
        respond(200, "{\"choices\":[]}");

        assertThat(failedOutcome(provider("sk-test", "some-model").callStructured(request()))
                .kind()).isEqualTo(LlmFailure.Kind.SCHEMA_MISMATCH);
    }

    @Test
    void aJsonAnswerInTheWrongShapeIsASchemaMismatch() {
        respond(200, answerEnvelope("""
                {"title":"t","skills":"not-a-list"}"""));

        assertThat(failedOutcome(provider("sk-test", "some-model").callStructured(request()))
                .kind()).isEqualTo(LlmFailure.Kind.SCHEMA_MISMATCH);
    }

    @Test
    void anUnreachableProviderAdvancesTheChain() {
        server.stop(0);

        var outcome = provider("sk-test", "some-model").callStructured(request());

        assertThat(failedOutcome(outcome).kind().tryNextProvider()).isTrue();
    }

    // ── F-014: no failure leaves without saying so ────────────────────────

    /**
     * The three transport failures used to return in silence, and because
     * every one of them advances the chain they never reached
     * {@code ProviderChain}'s "Chain stopped" line either. What the user saw
     * was ALL_PROVIDERS_UNAVAILABLE over an empty log.
     */
    @Test
    void anUnreachableProviderIsWrittenDownWithItsKindAndDetail() {
        server.stop(0);

        provider("sk-test", "some-model").callStructured(request());

        assertThat(warnings()).singleElement(as(STRING))
                .contains("UNREACHABLE")
                .contains("connection failed")
                .contains("job_analysis");
    }

    /**
     * The one the manual test actually hit: a 200 whose envelope carries no
     * message. It reads as a schema mismatch, which is the only kind the chain
     * retries — so the line has to name the kind, not just say "failed".
     */
    @Test
    void anEnvelopeWithNoContentIsWrittenDownAsASchemaMismatch() {
        respond(200, "{\"choices\":[]}");

        provider("sk-test", "some-model").callStructured(request());

        assertThat(warnings()).singleElement(as(STRING))
                .contains("SCHEMA_MISMATCH")
                .contains("no message content");
    }

    @Test
    void arejectedStatusIsWrittenDownToo() {
        respond(401, "{\"error\":{\"message\":\"nope\"}}");

        provider("sk-test", "some-model").callStructured(request());

        assertThat(warnings()).singleElement(as(STRING))
                .contains("REQUEST_REJECTED")
                .contains("http 401");
    }

    /**
     * Absolute rule 4. The prompt and the answer are both built from the
     * user's own content, and an error body can echo the prompt back.
     */
    @Test
    void whatIsWrittenDownCarriesNoPromptAndNoBody() {
        respond(500, "{\"error\":{\"message\":\"a posting leaked into the error\"}}");

        provider("sk-test", "some-model").callStructured(request());

        assertThat(warnings()).singleElement(as(STRING))
                .doesNotContain("a posting")
                .doesNotContain("You extract job postings")
                .doesNotContain("leaked");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private List<String> warnings() {
        return logged.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private LlmFailure failure(int status) {
        respond(status, "{\"error\":{\"message\":\"nope\"}}");
        return failedOutcome(provider("sk-test", "some-model").callStructured(request()));
    }

    private void respond(int status, String body) {
        nextStatus.set(new int[] {status});
        nextBody.set(body);
    }

    private com.fasterxml.jackson.databind.JsonNode readLastBody() {
        try {
            return JSON.readTree(lastBody.get());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static String answerEnvelope(String content) {
        try {
            return "{\"choices\":[{\"message\":{\"content\":"
                    + JSON.writeValueAsString(content) + "}}]}";
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private OpenRouterProvider provider(String key, String model) {
        return provider(key, model, OpenRouterProperties.StructuredOutput.JSON_SCHEMA);
    }

    private OpenRouterProvider provider(
            String key, String model, OpenRouterProperties.StructuredOutput mode) {
        return new OpenRouterProvider(
                new OpenRouterProperties(baseUrl, key, mode),
                new LlmProperties(Map.of(ModelTier.CHEAP, List.of("openrouter")),
                        Map.of("openrouter", model), Duration.ofSeconds(30), 0),
                JSON);
    }

    private static com.mustafatetik.atomcv.llm.gateway.LlmResponse<Analysis> answered(
            LlmOutcome<Analysis> outcome) {
        return ((LlmOutcome.Answered<Analysis>) outcome).response();
    }

    private static LlmFailure failedOutcome(LlmOutcome<Analysis> outcome) {
        return ((LlmOutcome.Failed<Analysis>) outcome).failure();
    }

    private static StructuredRequest<Analysis> request() {
        var schema = JSON.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject("title").put("type", "string");
        return new StructuredRequest<>("job_analysis", "v1", "You extract job postings.",
                "a posting", new JsonSchema("job_analysis", schema), Analysis.class,
                ModelTier.CHEAP, Duration.ofSeconds(10));
    }
}
