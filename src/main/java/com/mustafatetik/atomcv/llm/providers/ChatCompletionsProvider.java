package com.mustafatetik.atomcv.llm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mustafatetik.atomcv.llm.gateway.LlmFailure;
import com.mustafatetik.atomcv.llm.gateway.LlmOutcome;
import com.mustafatetik.atomcv.llm.gateway.LlmProperties;
import com.mustafatetik.atomcv.llm.gateway.LlmProvider;
import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
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

/**
 * The shape OpenAI defined and several vendors answer to.
 *
 * <p>Two rows of that table are the same protocol: OpenAI's own
 * {@code /v1/chat/completions} and DeepSeek's {@code /chat/completions}. They
 * differ in one field — whether the request may carry a JSON <em>schema</em>
 * or only ask for JSON — and in nothing else that this adapter touches.
 *
 * <p><strong>OpenRouter is deliberately not folded in here.</strong> It speaks
 * the same protocol and it is not the same adapter: it carries a routing policy
 * that is a privacy decision before it is a routing one, and it reports
 * {@code usage.cost}, which is the broker's own accounting and has no
 * equivalent at a vendor billing its own list price. Sharing a base with it
 * would mean two subclasses overriding half of it, which is not sharing.
 *
 * <p><strong>Nothing here logs a prompt or an answer</strong> (absolute rule
 * 4). What is logged is the status and the failure kind.
 */
abstract class ChatCompletionsProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionsProvider.class);

    private final HttpClient http;
    private final LlmProperties llm;
    private final ObjectMapper json;

    ChatCompletionsProvider(LlmProperties llm, ObjectMapper json) {
        this.llm = llm;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** Where {@code /chat/completions} hangs off, without a trailing slash. */
    abstract String baseUrl();

    abstract String apiKey();

    /**
     * Whether this vendor enforces a JSON <em>schema</em> or only promises
     * valid JSON.
     *
     * <p>Bolum 27.2 answers it per vendor and it is not detected: which
     * mechanism a model supports is a fact about the model, the response does
     * not state it reliably, and guessing from an error message would drop
     * every failure silently into the weaker mode — where the 99% conformance
     * target for Faz A stops holding.
     */
    abstract boolean supportsJsonSchema();

    @Override
    public boolean isAvailable() {
        return !apiKey().isEmpty() && !llm.modelFor(id()).isEmpty();
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
            // The status and the kind, never the body: an error body can echo
            // the prompt back.
            return failed(request, kindOf(status), "http " + status);
        }
        return parse(request, response.body(), System.nanoTime() - startedAt);
    }

    private <T> HttpRequest httpRequestFor(StructuredRequest<T> request) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/chat/completions"))
                .timeout(request.timeout())
                .header("Authorization", "Bearer " + apiKey())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(
                        bodyFor(request), StandardCharsets.UTF_8))
                .build();
    }

    private <T> String bodyFor(StructuredRequest<T> request) {
        ObjectNode body = json.createObjectNode();
        body.put("model", llm.modelFor(id()));
        var messages = body.putArray("messages");

        String system = request.systemPrompt();
        if (!supportsJsonSchema()) {
            // The weaker mode: the vendor promises valid JSON and nothing
            // about its shape, so the shape has to be asked for in words.
            // Appended to the system half, never inside the fence -- the
            // boundary is where the data starts, and a schema is ours.
            system = (system.isEmpty() ? "" : system + "\n\n")
                    + "Answer with JSON matching this schema:\n"
                    + request.outputSchema().node().toString();
        }
        if (!system.isEmpty()) {
            messages.addObject().put("role", "system").put("content", system);
        }
        messages.addObject().put("role", "user").put("content", request.userPrompt());

        body.set("response_format", responseFormat(request));
        return body.toString();
    }

    private <T> ObjectNode responseFormat(StructuredRequest<T> request) {
        ObjectNode format = json.createObjectNode();
        if (!supportsJsonSchema()) {
            return format.put("type", "json_object");
        }
        format.put("type", "json_schema");
        format.putObject("json_schema")
                .put("name", request.outputSchema().name())
                // Bolum 53.5 wants 99%+ schema conformance on Faz A. Without
                // strict the vendor reads the schema as a suggestion.
                .put("strict", true)
                .set("schema", request.outputSchema().node());
        return format;
    }

    private <T> LlmOutcome<T> parse(
            StructuredRequest<T> request, String body, long elapsedNanos) {
        try {
            JsonNode envelope = json.readTree(body);
            JsonNode content = envelope.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || !content.isTextual()) {
                return failed(request, LlmFailure.Kind.SCHEMA_MISMATCH, "no message content");
            }
            T value = json.treeToValue(json.readTree(content.asText()), request.resultType());
            JsonNode usage = envelope.path("usage");
            return LlmOutcome.answered(new LlmResponse<>(value, id(), llm.modelFor(id()),
                    usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").asInt(),
                    cachedTokens(usage),
                    elapsedNanos / 1_000_000,
                    // No usage.cost at a vendor billing its own list price:
                    // the pricing table is what answers here, and a zero would
                    // be a claim that the call was free.
                    null));
        } catch (Exception malformed) {
            // Never with the body attached: the answer is the user's content
            // rendered by a model.
            return failed(request, LlmFailure.Kind.SCHEMA_MISMATCH, "answer did not parse");
        }
    }

    /**
     * The discounted prefix, under either of the two spellings in use.
     *
     * <p>OpenAI reports it as {@code prompt_tokens_details.cached_tokens};
     * DeepSeek reports the same quantity as {@code prompt_cache_hit_tokens}.
     * Reading only one would price a cached call as fresh at the other vendor
     * — quietly, and always in the expensive direction.
     */
    private static int cachedTokens(JsonNode usage) {
        JsonNode nested = usage.path("prompt_tokens_details").path("cached_tokens");
        return nested.isNumber() ? nested.asInt() : usage.path("prompt_cache_hit_tokens").asInt();
    }

    /**
     * The routing, as HTTP states it. 408 is here because a proxy in front of
     * the vendor can answer it where the client saw no timeout.
     */
    static LlmFailure.Kind kindOf(int status) {
        if (status == 429) {
            return LlmFailure.Kind.RATE_LIMITED;
        }
        if (status == 408 || status == 504) {
            return LlmFailure.Kind.TIMEOUT;
        }
        if (status >= 500) {
            return LlmFailure.Kind.SERVER_ERROR;
        }
        // 400, 401, 403, 404: a bad key, a model that does not exist, or a
        // request this model cannot serve. Another vendor would not fix it.
        return LlmFailure.Kind.REQUEST_REJECTED;
    }

    /** F-014: every failed call leaves a line, and none of them carries content. */
    <T> LlmOutcome<T> failed(
            StructuredRequest<?> request, LlmFailure.Kind kind, String detail) {

        log.warn("{} did not answer prompt {}: {} ({})",
                id(), request.promptRef(), kind, detail);
        return LlmOutcome.failed(new LlmFailure(kind, id(), detail));
    }
}
