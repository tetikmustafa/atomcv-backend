package com.mustafatetik.atomcv.profile.domain.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * Reads and writes the {@code atom_variants.content} JSONB structure
 * (Bolum 14.1, 16.2).
 *
 * <p>Flyway cannot see inside a JSONB column, so the structure carries its own
 * version stamp and is upgraded lazily on read. Rows are rewritten in the
 * current version whenever they are saved; nothing rewrites the table at once.
 *
 * <p>The class holds no state and no dependencies, so it works both as a bean
 * and as a plain {@code new ContentMigrator()} inside a JPA converter.
 */
@Component
public class ContentMigrator {

    /** The structure version this build writes. */
    public static final int CURRENT_VERSION = 1;

    /** Keyed by the version being upgraded away from; empty while one version exists. */
    private static final Map<Integer, UnaryOperator<JsonNode>> UPGRADES = Map.of();

    /**
     * @throws IllegalArgumentException if the stored structure is malformed
     * @throws IllegalStateException    if it was written by a newer build
     */
    public RichContent read(JsonNode stored) {
        Objects.requireNonNull(stored, "stored");

        int version = stored.path("v").asInt(1);
        if (version < 1) {
            throw new IllegalArgumentException("Content version must be positive, was " + version);
        }
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException(
                    "Stored content is version " + version + ", this build understands up to "
                            + CURRENT_VERSION + ". Roll forward before reading it.");
        }

        JsonNode current = stored;
        while (version < CURRENT_VERSION) {
            UnaryOperator<JsonNode> upgrade = UPGRADES.get(version);
            if (upgrade == null) {
                throw new IllegalStateException("No upgrade registered from content version " + version);
            }
            current = upgrade.apply(current);
            version++;
        }
        return parse(current);
    }

    public ObjectNode write(RichContent content) {
        Objects.requireNonNull(content, "content");

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("v", CURRENT_VERSION);
        ArrayNode runs = root.putArray("runs");
        for (Run run : content.runs()) {
            ObjectNode node = runs.addObject();
            node.put("t", run.text());
            ArrayNode marks = node.putArray("m");
            for (Mark mark : run.marks()) {
                marks.add(mark.value());
            }
            if (run.href() != null) {
                node.put("href", run.href());
            }
        }
        return root;
    }

    private RichContent parse(JsonNode node) {
        JsonNode storedRuns = node.get("runs");
        if (storedRuns == null || !storedRuns.isArray()) {
            throw new IllegalArgumentException("Stored content has no runs array");
        }

        List<Run> runs = new ArrayList<>(storedRuns.size());
        for (JsonNode storedRun : storedRuns) {
            JsonNode text = storedRun.get("t");
            if (text == null || !text.isTextual()) {
                throw new IllegalArgumentException(
                        "Run " + runs.size() + " has no text"); // index only, never the text itself
            }
            runs.add(new Run(text.asText(), parseMarks(storedRun, runs.size()), href(storedRun)));
        }
        return new RichContent(runs);
    }

    private List<Mark> parseMarks(JsonNode storedRun, int index) {
        JsonNode stored = storedRun.get("m");
        if (stored == null || stored.isNull()) {
            return List.of();
        }
        if (!stored.isArray()) {
            throw new IllegalArgumentException("Run " + index + " has a non-array mark list");
        }
        List<Mark> marks = new ArrayList<>(stored.size());
        for (JsonNode mark : stored) {
            if (!mark.isTextual()) {
                throw new IllegalArgumentException("Run " + index + " has a non-textual mark");
            }
            marks.add(new Mark(mark.asText()));
        }
        return marks;
    }

    private String href(JsonNode storedRun) {
        JsonNode stored = storedRun.get("href");
        return stored == null || stored.isNull() ? null : stored.asText();
    }
}
