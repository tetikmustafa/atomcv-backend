package com.mustafatetik.atomcv.llm.prompts;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which version of each prompt is live, and what is being tried alongside it
 * (Bolum 53.2).
 *
 * <p>In configuration rather than in code so that a bad prompt is rolled back
 * by changing a value, not by cutting a release.
 *
 * @param active      prompt id to the version that serves everyone by default
 * @param experiments prompt id to the split being run on it, if any
 */
@ConfigurationProperties(prefix = "atomcv.prompts")
public record PromptProperties(
        Map<String, String> active,
        Map<String, Experiment> experiments) {

    public PromptProperties {
        active = active == null ? Map.of() : Map.copyOf(active);
        experiments = experiments == null ? Map.of() : Map.copyOf(experiments);
    }

    /**
     * A share of traffic sent to another version (Bolum 53.2).
     *
     * @param enabled    off by default, so an experiment left in configuration
     *                   after it ended does not keep running
     * @param variant    the version the sampled share receives
     * @param trafficPct how large that share is, 0-100
     */
    public record Experiment(boolean enabled, String variant, int trafficPct) {

        public Experiment {
            if (enabled) {
                if (variant == null || variant.isBlank()) {
                    throw new IllegalArgumentException("An enabled experiment needs a variant");
                }
                if (trafficPct < 0 || trafficPct > 100) {
                    throw new IllegalArgumentException(
                            "trafficPct is a percentage, got " + trafficPct);
                }
            }
        }
    }

    /** The experiment on this prompt, or null when there is none running. */
    public Experiment experiment(String promptId) {
        var experiment = experiments.get(promptId);
        return experiment != null && experiment.enabled() ? experiment : null;
    }
}
