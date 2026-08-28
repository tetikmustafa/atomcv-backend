package com.mustafatetik.atomcv.llm.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * The second adapter, and the first thing the chain can actually fall back to
 * (Bolum 27.3).
 *
 * <p>Until it existed the fallback chain had one link. {@code ProviderChainTest}
 * proved the walk works, the configuration named an order, and a single
 * OpenRouter outage still stopped the product — the mechanism was built and
 * had nothing to reach for. The audit of 2026-08-28 found this by reading
 * {@code llm/providers} rather than the tests.
 *
 * <p>A different vendor and not a second key at the same one: Bolum 27.3's
 * point is that the two do not fail together. OpenRouter is itself a broker,
 * so a second model behind it shares the broker's outage.
 *
 * <p><strong>Nothing here logs a prompt or an answer</strong> (absolute rule 4);
 * the status and the failure kind are what reach a log line.
 */
@Component
public class GeminiProvider implements LlmProvider {

    public static final String ID = "gemini";

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

    private final HttpClient http;
    private final GeminiProperties properties;
    private final LlmProperties llm;
    private final ObjectMapper json;

    public GeminiProvider(GeminiProperties properties, LlmProperties llm, ObjectMapper json) {
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

    /** Decided by the model configured for it, as with the other adapter. */
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

    /**
     * The key goes in a header rather than the query string {@code ?key=} that
     * Google's own examples use: a query string is what proxies and access logs
     * write down.
     */
    private <T> HttpRequest httpRequestFor(StructuredRequest<T> request) {
        String url = properties.baseUrl() + "/models/" + llm.modelFor(ID) + ":generateContent";
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(request.timeout())
                .header("x-goog-api-key", properties.apiKey())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(bodyFor(request),
                        StandardCharsets.UTF_8))
                .build();
    }

    private <T> String bodyFor(StructuredRequest<T> request) {
        var body = json.createObjectNode();

        if (!request.systemPrompt().isEmpty()) {
            // Its own field rather than a first turn: the system instruction is
            // held constant across calls, which is what a provider's prompt
            // cache discounts (Bolum 27.4).
            body.putObject("systemInstruction").putArray("parts")
                    .addObject().put("text", request.systemPrompt());
        }

        body.putArray("contents").addObject()
                .put("role", "user")
                .putArray("parts").addObject().put("text", request.userPrompt());

        var config = body.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        // Enforced by the provider, not asked for in words -- Bolum 53.5 wants
        // 99%+ conformance on Faz A. Narrowed first: see GeminiSchema.
        config.set("responseSchema", GeminiSchema.forResponse(request.outputSchema().node()));
        return body.toString();
    }

    private <T> LlmOutcome<T> parse(
            StructuredRequest<T> request, String body, long elapsedNanos) {
        try {
            var envelope = json.readTree(body);
            var content = envelope.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text");
            if (content.isMissingNode() || !content.isTextual()) {
                // Also how a safety block arrives: candidates comes back with
                // a finishReason and no parts. The kind is the same either way
                // — this vendor did not answer, and the next one may.
                return failed(request, LlmFailure.Kind.SCHEMA_MISMATCH, "no candidate text");
            }
            var value = json.treeToValue(json.readTree(content.asText()), request.resultType());
            var usage = envelope.path("usageMetadata");
            return LlmOutcome.answered(new LlmResponse<>(value, ID, llm.modelFor(ID),
                    usage.path("promptTokenCount").asInt(),
                    usage.path("candidatesTokenCount").asInt(),
                    // Absent is zero rather than unknown (Bolum 27.4), and it
                    // is a subset of the prompt count rather than an addition.
                    usage.path("cachedContentTokenCount").asInt(),
                    elapsedNanos / 1_000_000));
        } catch (Exception malformed) {
            return failed(request, LlmFailure.Kind.SCHEMA_MISMATCH, "answer did not parse");
        }
    }

    /** Bolum 27.3's routing, as this vendor states it. */
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
        // 400 here is usually a schema this vendor will not take, and asking
        // it again would buy the same refusal — but another vendor might well
        // accept it, so this stays a reason to move on rather than to stop.
        return LlmFailure.Kind.REQUEST_REJECTED;
    }

    private static <T> LlmOutcome<T> failed(
            StructuredRequest<?> request, LlmFailure.Kind kind, String detail) {

        log.warn("Gemini did not answer prompt {}: {} ({})", request.promptRef(), kind, detail);
        return LlmOutcome.failed(new LlmFailure(kind, ID, detail));
    }
}
