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
 * The first adapter (Bolum 27.2): one key, many models.
 *
 * <p>Raw REST rather than a vendor SDK, as Bolum 5.4 decides — the abstraction
 * that an SDK would provide is {@link LlmProvider}, and taking the dependency
 * would buy a second one that breaks on its own schedule.
 *
 * <p><strong>Nothing here logs a prompt or an answer.</strong> Both are built
 * from the user's own content (absolute rule 4). What is logged is the status
 * and the failure kind.
 */
@Component
public class OpenRouterProvider implements LlmProvider {

    public static final String ID = "openrouter";

    private static final Logger log = LoggerFactory.getLogger(OpenRouterProvider.class);

    private final HttpClient http;
    private final OpenRouterProperties properties;
    private final LlmProperties llm;
    private final ObjectMapper json;

    public OpenRouterProvider(
            OpenRouterProperties properties, LlmProperties llm, ObjectMapper json) {
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

    /** Bolum 27.3: no key is a silent skip, not a failure. */
    @Override
    public boolean isAvailable() {
        return properties.hasKey() && !llm.modelFor(ID).isEmpty();
    }

    /**
     * OpenRouter fronts every class of model, so which tier it serves is
     * decided by the model configured for it, not by the adapter.
     */
    @Override
    public ModelTier tier() {
        return ModelTier.CHEAP;
    }

    @Override
    public <T> LlmOutcome<T> callStructured(StructuredRequest<T> request) {
        long startedAt = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = http.send(httpRequestFor(request), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException timeout) {
            return failed(LlmFailure.Kind.TIMEOUT, "no answer within " + request.timeout());
        } catch (IOException unreachable) {
            return failed(LlmFailure.Kind.UNREACHABLE, "connection failed");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return failed(LlmFailure.Kind.UNREACHABLE, "interrupted");
        }

        var status = response.statusCode();
        if (status != 200) {
            var kind = kindOf(status);
            // The status and the kind, never the body: an error body can echo
            // the prompt back.
            log.warn("OpenRouter answered {} for prompt {}: {}",
                    status, request.promptRef(), kind);
            return failed(kind, "http " + status);
        }

        return parse(request, response.body(), System.nanoTime() - startedAt);
    }

    private <T> HttpRequest httpRequestFor(StructuredRequest<T> request) {
        return HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + "/chat/completions"))
                .timeout(request.timeout())
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(bodyFor(request),
                        StandardCharsets.UTF_8))
                .build();
    }

    private <T> String bodyFor(StructuredRequest<T> request) {
        var body = json.createObjectNode();
        body.put("model", llm.modelFor(ID));

        var messages = body.putArray("messages");
        var schema = request.outputSchema();
        var system = request.systemPrompt();
        if (properties.structuredOutput() == OpenRouterProperties.StructuredOutput.JSON_OBJECT) {
            // Bolum 27.2's weaker mode: the provider only promises valid JSON,
            // so the shape has to be asked for in words.
            system = (system.isEmpty() ? "" : system + "\n\n")
                    + "Answer with JSON matching this schema:\n" + schema.node().toString();
        }
        if (!system.isEmpty()) {
            messages.addObject().put("role", "system").put("content", system);
        }
        messages.addObject().put("role", "user").put("content", request.userPrompt());

        body.set("response_format", responseFormat(schema.name(), schema.node()));
        return body.toString();
    }

    private ObjectNode responseFormat(String name, JsonNode schema) {
        var format = json.createObjectNode();
        if (properties.structuredOutput() == OpenRouterProperties.StructuredOutput.JSON_OBJECT) {
            return format.put("type", "json_object");
        }
        format.put("type", "json_schema");
        format.putObject("json_schema")
                .put("name", name)
                // Bolum 53.5 wants 99%+ schema conformance on Faz A; strict is
                // what makes the provider enforce it rather than suggest it.
                .put("strict", true)
                .set("schema", schema);
        return format;
    }

    private <T> LlmOutcome<T> parse(
            StructuredRequest<T> request, String body, long elapsedNanos) {
        try {
            var envelope = json.readTree(body);
            var content = envelope.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || !content.isTextual()) {
                return failed(LlmFailure.Kind.SCHEMA_MISMATCH, "no message content");
            }
            var value = json.treeToValue(json.readTree(content.asText()), request.resultType());
            var usage = envelope.path("usage");
            return LlmOutcome.answered(new LlmResponse<>(value, ID, llm.modelFor(ID),
                    usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").asInt(),
                    // Reported only when the provider discounted a cached
                    // prefix; absent is zero, not unknown (Bolum 27.4).
                    usage.path("prompt_tokens_details").path("cached_tokens").asInt(),
                    elapsedNanos / 1_000_000));
        } catch (Exception malformed) {
            // Deliberately not logged with the body attached: the answer is
            // the user's content rendered by a model.
            log.warn("OpenRouter answer did not fit prompt {}", request.promptRef());
            return failed(LlmFailure.Kind.SCHEMA_MISMATCH, "answer did not parse");
        }
    }

    /**
     * Bolum 27.3's routing, as HTTP states it. 408 is here because a proxy in
     * front of the vendor can answer it where the client saw no timeout.
     */
    private static LlmFailure.Kind kindOf(int status) {
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

    private static <T> LlmOutcome<T> failed(LlmFailure.Kind kind, String detail) {
        return LlmOutcome.failed(new LlmFailure(kind, ID, detail));
    }
}
