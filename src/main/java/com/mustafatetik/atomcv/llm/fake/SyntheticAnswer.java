package com.mustafatetik.atomcv.llm.fake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mustafatetik.atomcv.llm.gateway.JsonSchema;
import java.util.Locale;

/**
 * An answer shaped like the schema, for calls no fixture covers (Bolum 54.2).
 *
 * <p>What this is for: working on the pipeline's logic and its error paths
 * without paying for a call, before any fixture has been recorded. It is not a
 * model — the values are placeholders. Anything asserting on <em>content</em>
 * belongs against a fixture or against the eval suite (Bolum 53.4).
 *
 * <p>Deterministic: the same schema and the same seed produce the same
 * document, so a test that fails does so for a reason rather than by draw.
 */
final class SyntheticAnswer {

    /** Enough items that a list-handling bug shows, few enough to stay legible. */
    private static final int ARRAY_ITEMS = 2;
    private static final int MAX_DEPTH = 12;

    private SyntheticAnswer() {
    }

    static JsonNode fromSchema(JsonSchema schema, long seed) {
        return build(schema.node(), seed, 0);
    }

    private static JsonNode build(JsonNode schema, long seed, int depth) {
        var nodes = JsonNodeFactory.instance;
        if (depth > MAX_DEPTH) {
            // A $ref-free schema cannot recurse, but a hand-written one with a
            // cycle would hang here rather than fail.
            return nodes.nullNode();
        }
        if (schema.has("enum") && schema.get("enum").isArray()
                && !schema.get("enum").isEmpty()) {
            var options = schema.get("enum");
            return options.get((int) Math.floorMod(seed, options.size()));
        }
        if (schema.has("const")) {
            return schema.get("const");
        }

        return switch (typeOf(schema)) {
            case "object" -> objectFrom(schema, seed, depth, nodes);
            case "array" -> arrayFrom(schema, seed, depth, nodes);
            case "integer" -> nodes.numberNode(Math.floorMod(seed, 100));
            case "number" -> nodes.numberNode(Math.floorMod(seed, 1000) / 10.0);
            case "boolean" -> nodes.booleanNode(seed % 2 == 0);
            case "null" -> nodes.nullNode();
            default -> nodes.textNode("synthetic-" + Math.floorMod(seed, 1000));
        };
    }

    private static ObjectNode objectFrom(
            JsonNode schema, long seed, int depth, JsonNodeFactory nodes) {
        var object = nodes.objectNode();
        var properties = schema.get("properties");
        if (properties == null || !properties.isObject()) {
            return object;
        }
        // Every declared property, not only the required ones: a consumer that
        // reads an optional field would otherwise never be exercised locally.
        properties.properties().forEach(property ->
                object.set(property.getKey(),
                        build(property.getValue(), seed + property.getKey().hashCode(), depth + 1)));
        return object;
    }

    private static ArrayNode arrayFrom(
            JsonNode schema, long seed, int depth, JsonNodeFactory nodes) {
        var array = nodes.arrayNode();
        var items = schema.get("items");
        if (items == null || !items.isObject()) {
            return array;
        }
        int count = Math.max(schema.path("minItems").asInt(ARRAY_ITEMS), 1);
        for (int index = 0; index < Math.min(count, ARRAY_ITEMS + 1); index++) {
            array.add(build(items, seed + index, depth + 1));
        }
        return array;
    }

    /**
     * JSON Schema allows {@code type} to be a list. The first entry is as good
     * a choice as any for a placeholder.
     */
    private static String typeOf(JsonNode schema) {
        var type = schema.get("type");
        if (type == null) {
            return schema.has("properties") ? "object" : "string";
        }
        var name = type.isArray() && !type.isEmpty() ? type.get(0).asText() : type.asText();
        // Locale.ROOT: absolute rule 7.
        return name.toLowerCase(Locale.ROOT);
    }
}
