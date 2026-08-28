package com.mustafatetik.atomcv.llm.providers;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The second vendor's credentials (Bolum 27.3).
 *
 * @param apiKey  from Google AI Studio. Absent means the provider is skipped
 *                silently, exactly as OpenRouter's absence is — a deployment
 *                with one key out of two is the normal case, not a degraded one
 * @param baseUrl overridable so a test can point it at a socket it owns
 */
@ConfigurationProperties(prefix = "atomcv.llm.gemini")
public record GeminiProperties(String apiKey, String baseUrl) {

    public GeminiProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://generativelanguage.googleapis.com/v1beta"
                : baseUrl.trim();
    }

    public boolean hasKey() {
        return !apiKey.isEmpty();
    }
}
