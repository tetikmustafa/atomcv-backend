package com.mustafatetik.atomcv.llm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * A prompt's JSON Schema, narrowed to what Gemini's {@code responseSchema}
 * accepts.
 *
 * <p>Gemini does not take JSON Schema. It takes a subset of OpenAPI 3.0, and a
 * document carrying anything outside that subset is refused with a 400 — not
 * ignored. Every schema in {@code resources/prompts} carries
 * {@code additionalProperties}, so passing them through unchanged would have
 * failed <em>every</em> call to this provider, and the chain would have read it
 * as "Gemini is down" for as long as nobody looked at the detail.
 *
 * <p>Three keywords are dropped and the rest is already inside the subset:
 * measured across all six prompt schemas, the vocabulary in use is
 * {@code type}, {@code items}, {@code properties}, {@code required},
 * {@code enum}, {@code minimum}, {@code maximum} and
 * {@code additionalProperties}. The first five are supported; the last three
 * are not.
 *
 * <p><strong>Dropping a constraint weakens the schema, and that is the
 * trade.</strong> {@code additionalProperties: false} stops a model inventing
 * a field, and without it one can — but the answer is parsed into a record,
 * and Jackson ignores what the record does not declare. {@code minimum} and
 * {@code maximum} are asserted again by the validators that read the value
 * ({@code PlausibilityGate} checks confidence itself). Nothing that matters is
 * enforced only here.
 */
final class GeminiSchema {

    /** Valid JSON Schema, outside Gemini's OpenAPI subset. */
    private static final List<String> UNSUPPORTED =
            List.of("additionalProperties", "minimum", "maximum", "$schema", "definitions");

    private GeminiSchema() {
    }

    /** A copy, narrowed. The original belongs to the prompt registry. */
    static JsonNode forResponse(JsonNode schema) {
        return narrow(schema.deepCopy());
    }

    private static JsonNode narrow(JsonNode node) {
        if (node instanceof ObjectNode object) {
            UNSUPPORTED.forEach(object::remove);
            nullableInsteadOfUnion(object);
            object.properties().forEach(field -> narrow(field.getValue()));
        } else if (node instanceof ArrayNode array) {
            array.forEach(GeminiSchema::narrow);
        }
        return node;
    }

    /**
     * {@code "type": ["string", "null"]} becomes {@code "type": "string",
     * "nullable": true}.
     *
     * <p>JSON Schema says "may be null" with a type union; Gemini's subset has
     * one type per node and a flag beside it, and a list where it expects one
     * value is a 400 ("Proto field is not repeating"). The vocabulary survey
     * above missed this because a union is a value of {@code type}, not a
     * keyword of its own — and {@code profile_extraction} has ten of them, so
     * every import that reached this provider was refused, measured against the
     * live API on 2026-10-24.
     */
    private static void nullableInsteadOfUnion(ObjectNode object) {
        if (!(object.get("type") instanceof ArrayNode union)) {
            return;
        }
        String single = null;
        boolean nullable = false;
        for (JsonNode member : union) {
            if ("null".equals(member.asText())) {
                nullable = true;
            } else if (single == null) {
                single = member.asText();
            }
        }
        if (single != null) {
            object.put("type", single);
        } else {
            object.remove("type");
        }
        if (nullable) {
            object.put("nullable", true);
        }
    }
}
