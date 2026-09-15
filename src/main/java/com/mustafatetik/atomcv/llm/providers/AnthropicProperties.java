package com.mustafatetik.atomcv.llm.providers;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Anthropic's endpoint.
 *
 * @param apiKey     {@code ANTHROPIC_API_KEY}; blank means the provider is
 *  skipped silently
 * @param baseUrl    overridable so a test can point it at a socket it owns
 * @param version    the {@code anthropic-version} header, which this API
 *                   requires on every request and which pins the response
 *                   shape this adapter parses. A property rather than a
 *                   constant because moving it is a deployment's decision to
 *                   make before the code is ready for it, and finding out by
 *                   reading a parse failure is the expensive way
 * @param maxTokens  the answer ceiling, which this API requires and the others
 *                   default. Four thousand is well past the largest schema
 *                   here -- a profile extraction -- and a ceiling that clips
 *                   an answer produces valid JSON that stops mid-object, which
 *                   arrives as a schema mismatch and is retried into the same
 *                   ceiling
 */
@ConfigurationProperties(prefix = "atomcv.llm.anthropic")
public record AnthropicProperties(
        String apiKey, String baseUrl, String version, int maxTokens) {

    public AnthropicProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://api.anthropic.com/v1"
                : baseUrl.trim();
        version = version == null || version.isBlank() ? "2023-06-01" : version.trim();
        maxTokens = maxTokens <= 0 ? 4096 : maxTokens;
    }
}
