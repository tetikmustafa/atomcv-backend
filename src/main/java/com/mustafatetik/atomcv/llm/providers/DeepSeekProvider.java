package com.mustafatetik.atomcv.llm.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.gateway.LlmProperties;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import org.springframework.stereotype.Component;

/**
 * The DeepSeek row, and the one entry in that table with no schema mode at
 * all: {@code response_format: json_object}, the shape asked for in the
 * prompt.
 *
 * <p><strong>That is a real difference, not a configuration one.</strong> A
 * vendor that only promises valid JSON will sometimes return valid JSON of the
 * wrong shape, which arrives here as a SCHEMA_MISMATCH and is retried in
 * place. Faz A is held to 99% conformance, so this link belongs behind one
 * that enforces a schema rather than in front of it -- a chain naming it first
 * would pay the retry on every generation.
 */
@Component
public class DeepSeekProvider extends ChatCompletionsProvider {

    public static final String ID = "deepseek";

    private final DeepSeekProperties properties;

    public DeepSeekProvider(DeepSeekProperties properties, LlmProperties llm, ObjectMapper json) {
        super(llm, json);
        this.properties = properties;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ModelTier tier() {
        return ModelTier.CHEAP;
    }

    @Override
    String baseUrl() {
        return properties.baseUrl();
    }

    @Override
    String apiKey() {
        return properties.apiKey();
    }

    @Override
    boolean supportsJsonSchema() {
        return false;
    }
}
