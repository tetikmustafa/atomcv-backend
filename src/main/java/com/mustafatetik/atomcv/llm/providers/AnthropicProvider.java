package com.mustafatetik.atomcv.llm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mustafatetik.atomcv.llm.gateway.LlmFailure;
import com.mustafatetik.atomcv.llm.gateway.LlmOutcome;
import com.mustafatetik.atomcv.llm.gateway.LlmProperties;
import com.mustafatetik.atomcv.llm.gateway.LlmProvider;
import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bolum 27.2's Anthropic row, and the reason that table has a column for the
 * mechanism at all.
 *
 * <p><strong>There is no bare JSON mode.</strong> Every other vendor here is
 * asked for JSON and answers with a string that parses; this one is given a
 * single tool whose input schema <em>is</em> the output schema, told it must
 * call that tool, and the answer is read out of the {@code tool_use} block's
 * {@code input}. The effect is the same guarantee by a different route — the
 * API validates the tool input against the schema — and it is a separate code
 * path rather than a flag, which is what Bolum 27.2 means by "ayri kod yolu".
 *
 * <p>Not extending {@link ChatCompletionsProvider}: it shares the transport and
 * nothing above it. The request body, the message shape, the way the system
 * prompt travels (a top-level field here, not a message) and the whole of the
 * response parse are different, and a base class overridden that far down is a
 * base class in name.
 *
 * <p><strong>Nothing here logs a prompt or an answer</strong> (absolute rule
 * 4).
 */
@Component
public class AnthropicProvider implements LlmProvider {

    public static final String ID = "anthropic";

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);

    private final HttpClient http;
    private final AnthropicProperties properties;
    private final LlmProperties llm;
    private final ObjectMapper json;

    public AnthropicProvider(
            AnthropicProperties properties, LlmProperties llm, ObjectMapper json) {
        this.properties = properties;
        this.llm = llm;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return !properties.apiKey().isEmpty() && !llm.modelFor(ID).isEmpty();
    }

    @Override
    public ModelTier tier() {
        return ModelTier.MID;
    }

    @Override
    public <T> LlmOutcome<T> callStructured(StructuredRequest<T> request) {
        long startedAt = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = http.send(httpRequestFor(request), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException timeout) {
            return failed(request, LlmFailure.Kind.TIMEOUT,
                    "no answer within " + request.timeout());
        } catch (IOException unreachable) {
            return failed(request, LlmFailure.Kind.UNREACHABLE, "connection failed");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return failed(request, LlmFailure.Kind.UNREACHABLE, "interrupted");
        }

        int status = response.statusCode();
        if (status != 200) {
            return failed(request, ChatCompletionsProvider.kindOf(status), "http " + status);
        }
        return parse(request, response.body(), System.nanoTime() - startedAt);
    }

    private <T> HttpRequest httpRequestFor(StructuredRequest<T> request) {
        return HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + "/messages"))
                .timeout(request.timeout())
                // Not a Bearer token: this API takes the key in its own header
                // and rejects the Authorization form outright.
                .header("x-api-key", properties.apiKey())
                .header("anthropic-version", properties.version())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(
                        bodyFor(request), StandardCharsets.UTF_8))
                .build();
    }

    private <T> String bodyFor(StructuredRequest<T> request) {
        ObjectNode body = json.createObjectNode();
        body.put("model", llm.modelFor(ID));
        body.put("max_tokens", properties.maxTokens());

        // The system prompt is a top-level field, not a message with a role.
        // Bolum 18.3 sends two messages precisely so the instructions and the
        // data are separable; here the separation is structural and stronger.
        if (!request.systemPrompt().isEmpty()) {
            body.put("system", request.systemPrompt());
        }
        body.putArray("messages").addObject()
                .put("role", "user")
                .put("content", request.userPrompt());

        // One tool, and the schema is its input. Bolum 27.2: this is how a
        // schema is enforced here, and the name has to match the tool_choice
        // below or the API refuses the request.
        String toolName = request.outputSchema().name();
        ObjectNode tool = body.putArray("tools").addObject();
        tool.put("name", toolName);
        tool.put("description",
                "Return the result in this structure. This is the only way to answer.");
        tool.set("input_schema", request.outputSchema().node());

        body.putObject("tool_choice").put("type", "tool").put("name", toolName);
        return body.toString();
    }

    private <T> LlmOutcome<T> parse(
            StructuredRequest<T> request, String body, long elapsedNanos) {
        try {
            JsonNode envelope = json.readTree(body);
            JsonNode input = toolInput(envelope);
            if (input == null) {
                // The model answered in prose instead of calling the tool.
                // A schema mismatch, and Bolum 27.3 retries it here rather
                // than walking to a vendor that would do the same thing.
                return failed(request, LlmFailure.Kind.SCHEMA_MISMATCH, "no tool_use block");
            }
            T value = json.treeToValue(input, request.resultType());
            JsonNode usage = envelope.path("usage");
            return LlmOutcome.answered(new LlmResponse<>(value, ID, llm.modelFor(ID),
                    usage.path("input_tokens").asInt(),
                    usage.path("output_tokens").asInt(),
                    // Prompt caching is reported in two halves here; the read
                    // is the discounted one. The write is charged above list
                    // and is already inside input_tokens.
                    usage.path("cache_read_input_tokens").asInt(),
                    elapsedNanos / 1_000_000,
                    null));
        } catch (Exception malformed) {
            return failed(request, LlmFailure.Kind.SCHEMA_MISMATCH, "answer did not parse");
        }
    }

    /**
     * The tool call's input, out of a content array that may hold other blocks.
     *
     * <p>Scanned rather than indexed at zero: a model may emit a {@code text}
     * block before the {@code tool_use} one — thinking out loud before
     * answering — and reading position zero would turn a perfectly good answer
     * into a schema mismatch, on the model's whim, in production.
     *
     * @return null when no tool call is present at all
     */
    private static JsonNode toolInput(JsonNode envelope) {
        for (JsonNode block : envelope.path("content")) {
            if ("tool_use".equals(block.path("type").asText())) {
                JsonNode input = block.path("input");
                return input.isObject() ? input : null;
            }
        }
        return null;
    }

    /** F-014: every failed call leaves a line, and none of them carries content. */
    private static <T> LlmOutcome<T> failed(
            StructuredRequest<?> request, LlmFailure.Kind kind, String detail) {

        log.warn("Anthropic did not answer prompt {}: {} ({})",
                request.promptRef(), kind, detail);
        return LlmOutcome.failed(new LlmFailure(kind, ID, detail));
    }
}
