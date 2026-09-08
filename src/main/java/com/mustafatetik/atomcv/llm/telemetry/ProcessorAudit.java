package com.mustafatetik.atomcv.llm.telemetry;

import com.mustafatetik.atomcv.llm.gateway.LlmProperties;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Who this deployment may send a person's content to (EK C.1, Bolum 57).
 *
 * <p>EK C.1's pre-launch item is "the AI provider list is current and public",
 * and until now the only way to check it was to read three configuration files
 * and remember what a fourth one implied. The list is not a fact about the
 * product; it is a fact about <em>this deployment's configuration</em> — the
 * chain names providers, each provider has a key and a model, and any of them
 * with both may receive a CV. So the deployment says it, at startup, in the
 * words the published page has to match.
 *
 * <p><strong>What it cannot say is where a broker sends it next.</strong>
 * OpenRouter's routing is invisible in the answer: one model slug of ours is
 * served by seven endpoints across four organisations, and which one answered a
 * given call is not reported. The line says so rather than implying a shorter
 * list than the truth — and pinning `atomcv.llm.openrouter.only` is what makes
 * it shorter, deliberately and per deployment.
 *
 * <p>Names and model ids only. No key, no prompt, no content (absolute rule 4);
 * everything here is already in a configuration file somebody can read.
 */
@Component
public class ProcessorAudit {

    private static final Logger log = LoggerFactory.getLogger(ProcessorAudit.class);

    private final LlmProperties properties;

    ProcessorAudit(LlmProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reportConfiguredProcessors() {
        List<String> processors = configuredProcessors();
        if (processors.isEmpty()) {
            // Every provider switched off. Not a warning: this is what the fake
            // profile looks like, and what a deployment with no keys yet looks
            // like — nothing can be sent anywhere.
            log.info("No LLM provider is configured; no content can leave this deployment.");
            return;
        }
        log.info("Content may be sent to {} — and onward wherever a broker routes it, "
                + "which the answer does not say. EK C.1's published list has to name "
                + "the same processors.", processors);
    }

    /**
     * The providers a call can actually reach, with the model each would run.
     *
     * <p>A chain entry with no model configured is a provider Bolum 27.3 skips,
     * so it cannot receive anything and does not belong in a list of who does.
     * Ordered as the chain is, because that is the order content is offered in.
     *
     * <p>Package-private: this is the assertion an operator's checklist rests
     * on, and it is worth being able to make it without a context.
     */
    List<String> configuredProcessors() {
        Set<String> providers = new LinkedHashSet<>();
        for (ModelTier tier : ModelTier.values()) {
            providers.addAll(properties.chainFor(tier));
        }
        List<String> configured = new ArrayList<>();
        for (String provider : providers) {
            String model = properties.modelFor(provider);
            if (!model.isBlank()) {
                configured.add(provider + "=" + model);
            }
        }
        return configured;
    }
}
