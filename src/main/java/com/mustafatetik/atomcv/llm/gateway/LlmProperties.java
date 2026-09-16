package com.mustafatetik.atomcv.llm.gateway;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which providers serve which tier, and in what order.
 *
 * <p>Order is the whole configuration: a chain is walked front to back and the
 * first provider with a key that answers wins. Model ids are environment
 * variables rather than literals because vendors rename models faster than a
 * release cycle.
 *
 * <p><strong>There is no timeout here and there used to be one that did
 * nothing</strong> (denetim, beşinci tur). {@code atomcv.llm.call-timeout} was
 * bound, defaulted and documented, and no line read it: every caller carries
 * its own ceiling, because they are not comparable — an extraction reads a
 * whole CV and is given two minutes, an edit returns line numbers and is given
 * twenty seconds. One number for both would have to be the larger, which is
 * not a timeout for the second. A knob an operator can turn without anything
 * changing is worse than no knob.
 *
 * @param chain       tier to the provider ids that serve it, in order
 * @param models      provider id to the model it should ask for
 * @param schemaRetries how many times a schema mismatch is retried on the same
 *                    provider before the walk stops. The rule is to retry
 *                    there rather than move on but does not say how often; one
 *                    retry catches a model that simply wandered, and more
 *                    would be paying repeatedly for a prompt that is wrong.
 */
@ConfigurationProperties(prefix = "atomcv.llm")
public record LlmProperties(
        Map<ModelTier, List<String>> chain,
        Map<String, String> models,
        int schemaRetries) {

    public LlmProperties {
        chain = chain == null ? Map.of() : Map.copyOf(chain);
        models = models == null ? Map.of() : Map.copyOf(models);
        if (schemaRetries < 0) {
            throw new IllegalArgumentException("schemaRetries cannot be negative");
        }
    }

    /** The ids serving this tier, in order. Empty when none is configured. */
    public List<String> chainFor(ModelTier tier) {
        return chain.getOrDefault(tier, List.of());
    }

    /** The model id for a provider, or empty when the environment has none. */
    public String modelFor(String providerId) {
        var model = models.get(providerId);
        return model == null ? "" : model;
    }
}
