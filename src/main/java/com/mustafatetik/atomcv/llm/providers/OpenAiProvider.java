package com.mustafatetik.atomcv.llm.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.gateway.LlmProperties;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import org.springframework.stereotype.Component;

/**
 * Bolum 27.2's OpenAI row: {@code response_format: json_schema, strict: true}.
 *
 * <p>The MID tier by default (Bolum 5.4's table): the two jobs that ask for
 * one are the About synthesis and profile extraction, and both are judged on
 * how well they write rather than on how cheaply. Which model actually answers
 * is still {@code OPENAI_MODEL} -- the tier says what this link is for, not
 * what it costs.
 */
@Component
public class OpenAiProvider extends ChatCompletionsProvider {

    public static final String ID = "openai";

    private final OpenAiProperties properties;

    public OpenAiProvider(OpenAiProperties properties, LlmProperties llm, ObjectMapper json) {
        super(llm, json);
        this.properties = properties;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ModelTier tier() {
        return ModelTier.MID;
    }

    @Override
    String baseUrl() {
        return properties.baseUrl();
    }

    @Override
    String apiKey() {
        return properties.apiKey();
    }

    /** The vendor that defined the mechanism. */
    @Override
    boolean supportsJsonSchema() {
        return true;
    }
}
