package com.mustafatetik.atomcv.generation.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What produced this document (Bolum 14.7).
 *
 * <p>Stored because a generation is reproducible only against the engine that
 * made it: a scoring weight change or a template revision moves the output,
 * and without this the difference between "the user edited their profile" and
 * "we shipped a new renderer" is unanswerable after the fact.
 *
 * @param template        {@code id:vN}, the customization's own cost key
 * @param promptVersions  which version of each prompt ran (Bolum 53.2)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EngineVersion(
        String pipeline, String scoringWeights, String template,
        Map<String, String> promptVersions) {

    /** Bumped when a change to the pipeline moves output for unchanged input. */
    public static final String PIPELINE = "2.0.0";

    public EngineVersion {
        pipeline = pipeline == null ? PIPELINE : pipeline;
        scoringWeights = scoringWeights == null ? "" : scoringWeights;
        template = template == null ? "" : template;
        // Ordered: this is a JSONB column, and Map.copyOf iterates in an order
        // salted per JVM run — the same engine would serialise differently on
        // every restart (CLAUDE.md).
        promptVersions = promptVersions == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(promptVersions));
    }
}
