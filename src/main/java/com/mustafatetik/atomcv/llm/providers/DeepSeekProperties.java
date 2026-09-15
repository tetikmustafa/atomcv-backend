package com.mustafatetik.atomcv.llm.providers;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek's endpoint.
 *
 * @param apiKey  {@code DEEPSEEK_API_KEY}; blank means the provider is skipped
 *  silently
 * @param baseUrl overridable so a test can point it at a socket it owns
 */
@ConfigurationProperties(prefix = "atomcv.llm.deepseek")
public record DeepSeekProperties(String apiKey, String baseUrl) {

    public DeepSeekProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://api.deepseek.com"
                : baseUrl.trim();
    }
}
