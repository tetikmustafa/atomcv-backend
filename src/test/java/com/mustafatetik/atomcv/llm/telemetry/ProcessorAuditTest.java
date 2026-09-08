package com.mustafatetik.atomcv.llm.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.llm.gateway.LlmProperties;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The list EK C.1 says has to be current and public.
 *
 * <p>It is a fact about configuration rather than about the product, which is
 * why it is computed rather than written down: a deployment that adds a vendor
 * adds a processor, and a published page that still names one is out of date the
 * moment the chain changes.
 */
class ProcessorAuditTest {

    @Test
    void aconfiguredProviderWithAModelIsAProcessor() {
        var audit = audit(
                Map.of(ModelTier.CHEAP, List.of("openrouter", "gemini")),
                Map.of("openrouter", "openai/gpt-5.6-sol", "gemini", "gemini-3-flash"));

        assertThat(audit.configuredProcessors())
                .containsExactly("openrouter=openai/gpt-5.6-sol", "gemini=gemini-3-flash");
    }

    /**
     * A provider with no model is one Bolum 27.3 skips, so it receives nothing
     * and does not belong on a list of who does. This is the shape of a
     * deployment that has configured one vendor of the two.
     */
    @Test
    void aproviderWithNoModelReceivesNothingAndIsNotListed() {
        var audit = audit(
                Map.of(ModelTier.CHEAP, List.of("openrouter", "gemini")),
                Map.of("openrouter", "openai/gpt-5.6-sol", "gemini", ""));

        assertThat(audit.configuredProcessors()).containsExactly("openrouter=openai/gpt-5.6-sol");
    }

    /** Both tiers, deduplicated: content is offered to a provider, not to a tier. */
    @Test
    void aproviderNamedByBothTiersIsOneProcessor() {
        var audit = audit(
                Map.of(ModelTier.CHEAP, List.of("openrouter"),
                        ModelTier.MID, List.of("openrouter")),
                Map.of("openrouter", "openai/gpt-5.6-sol"));

        assertThat(audit.configuredProcessors()).containsExactly("openrouter=openai/gpt-5.6-sol");
    }

    /**
     * And the honest answer for a deployment with nothing configured — which is
     * what `local-fake` is, and what a fresh clone is.
     */
    @Test
    void nothingConfiguredMeansNothingCanLeave() {
        var audit = audit(Map.of(ModelTier.CHEAP, List.of("fake")), Map.of());

        assertThat(audit.configuredProcessors()).isEmpty();
    }

    private static ProcessorAudit audit(
            Map<ModelTier, List<String>> chain, Map<String, String> models) {

        return new ProcessorAudit(
                new LlmProperties(chain, models, Duration.ofSeconds(30), 0));
    }
}
