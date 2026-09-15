package com.mustafatetik.atomcv.llm.providers;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI's own endpoint, not the broker's.
 *
 * @param apiKey  {@code OPENAI_API_KEY}; blank means the provider is skipped
 *  silently, which is the normal state of a
 *                deployment that has configured two vendors out of five
 * @param baseUrl overridable so a test can point it at a socket it owns
 */
@ConfigurationProperties(prefix = "atomcv.llm.openai")
public record OpenAiProperties(String apiKey, String baseUrl) {

    public OpenAiProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://api.openai.com/v1"
                : baseUrl.trim();
    }
}
