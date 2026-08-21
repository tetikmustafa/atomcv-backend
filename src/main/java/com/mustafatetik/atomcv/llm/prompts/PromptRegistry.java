package com.mustafatetik.atomcv.llm.prompts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.gateway.JsonSchema;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import org.springframework.stereotype.Component;

/**
 * The prompts on disk, at the version configuration says to use
 * (Bolum 53.1-53.3).
 *
 * <p>Files, not rows: a prompt and the code that consumes its answer — the
 * schema, the parse, the validator — change in the same commit. Stored in the
 * database they would be deployable apart, and would drift (Bolum 53.1).
 *
 * <p>Loaded once and cached. The resources are immutable for the life of the
 * process, so re-reading them per call would be work repeated for nothing.
 */
@Component
public class PromptRegistry {

    private static final String ROOT = "prompts/";

    private final PromptProperties properties;
    private final ObjectMapper json;
    private final Map<String, Prompt> cache = new ConcurrentHashMap<>();

    public PromptRegistry(PromptProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    /**
     * The version this call should run (Bolum 53.3).
     *
     * <p>{@code bucketKey} is the user id, not the request id: a user who saw
     * one variant must keep seeing it, or an experiment measures the variance
     * of a single person's session rather than the difference between two
     * prompts.
     */
    public String selectVersion(String promptId, String bucketKey) {
        var active = activeVersion(promptId);
        var experiment = properties.experiment(promptId);
        if (experiment == null || bucketKey == null || bucketKey.isBlank()) {
            return active;
        }
        return bucket(promptId + ":" + bucketKey) < experiment.trafficPct()
                ? experiment.variant()
                : active;
    }

    /** The version everyone gets when no experiment is running. */
    public String activeVersion(String promptId) {
        var version = properties.active().get(promptId);
        if (version == null || version.isBlank()) {
            throw new IllegalStateException(
                    "No active version configured for prompt " + promptId
                            + " — set atomcv.prompts.active." + promptId);
        }
        return version;
    }

    /** The active version's text and schema. */
    public Prompt load(String promptId) {
        return load(promptId, activeVersion(promptId));
    }

    public Prompt load(String promptId, String version) {
        return cache.computeIfAbsent(promptId + ":" + version, key -> read(promptId, version));
    }

    /**
     * Reads every configured prompt, so that a missing file is a failure at
     * startup rather than on the first user's generation.
     *
     * <p>Called explicitly rather than from a lifecycle hook: the fake
     * provider's tests configure prompts that no file backs, and a registry
     * that validated itself on construction could not be built for them.
     */
    public void validateConfiguredPrompts() {
        properties.active().keySet().forEach(this::load);
        properties.experiments().forEach((promptId, experiment) -> {
            if (experiment.enabled()) {
                load(promptId, experiment.variant());
            }
        });
    }

    private Prompt read(String promptId, String version) {
        var text = readResource(ROOT + promptId + "/" + version + ".md");
        var schemaText = readResource(ROOT + promptId + "/schema.json");
        try {
            return new Prompt(promptId, version, text,
                    new JsonSchema(promptId, json.readTree(schemaText)));
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Schema for prompt " + promptId + " is not JSON", e);
        }
    }

    private String readResource(String path) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("No such prompt resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }

    /**
     * A stable 0-99 bucket.
     *
     * <p>Bolum 53.3 uses Guava's murmur3_32. CRC32 is in the JDK, is specified
     * rather than implementation-defined, and is spread well enough to split
     * traffic into a hundred buckets — a dependency the size of Guava for one
     * hash is not worth it. It also avoids the trap in the snippet:
     * {@code Math.abs} on {@code Integer.MIN_VALUE} is still negative, and the
     * modulo of a negative int is negative. CRC32 returns an unsigned long.
     */
    private static int bucket(String key) {
        var crc = new CRC32();
        crc.update(key.getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % 100);
    }
}
