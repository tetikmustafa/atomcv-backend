package com.mustafatetik.atomcv.llm.gateway;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The shape a provider must answer in.
 *
 * <p>The type is named without being defined anywhere. It is a value object
 * over the {@code schema.json} that sits beside each prompt, carrying the
 * parsed document and the name providers need: OpenAI and OpenRouter put a
 * schema name in {@code response_format}, and Anthropic uses it as the forced
 * tool's name.
 *
 * <p>Parsed rather than raw text, because every adapter embeds it in a request
 * body and a re-parse per call would be work repeated for nothing.
 *
 * @param name   identifies the schema to the provider; also the tool name for
 *               the Anthropic adapter's forced tool call
 * @param node   the JSON Schema document itself
 */
public record JsonSchema(String name, JsonNode node) {

    public JsonSchema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A schema needs a name");
        }
        if (node == null || node.isEmpty()) {
            throw new IllegalArgumentException("Schema " + name + " has no document");
        }
    }
}
