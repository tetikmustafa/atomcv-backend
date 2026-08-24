package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.pipeline.GeneratedDocument;
import com.mustafatetik.atomcv.generation.scoring.ScoringWeights;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A finished job-specific generation, with everything the record needs.
 *
 * <p>{@link GeneratedDocument} is what the pipeline produces and it is
 * deliberately thin — bytes, pages, and the selection. Persisting a generation
 * needs four more facts that only this service ever held: whose profile it
 * was, what Faz A made of the posting, what was asked for, and which weight
 * set Faz B ran with. Returning them beats having the handler re-derive any of
 * them, which is how a record ends up describing a different run than the one
 * that happened.
 *
 * @param weights        which of Bolum 28.4's two sets scored this run. It
 *                       goes into {@code engine_version}, because a week of
 *                       generations scored without vectors is otherwise
 *                       indistinguishable from a prompt regression.
 * @param promptVersions the versions that actually ran, which under an A/B
 *                       experiment is not the same as the configured defaults
 *                       (Bolum 53.3)
 */
public record GeneratedGeneration(
        UUID profileId,
        JobAnalysis posting,
        GenerationOptions options,
        ScoringWeights weights,
        Map<String, String> promptVersions,
        GeneratedDocument document) {

    public GeneratedGeneration {
        promptVersions = promptVersions == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(promptVersions));
    }
}
