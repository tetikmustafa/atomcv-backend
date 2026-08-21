package com.mustafatetik.atomcv.llm.gateway;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which providers serve which tier, and in what order (Bolum 27.3).
 *
 * <p>Order is the whole configuration: a chain is walked front to back and the
 * first provider with a key that answers wins. Model ids are environment
 * variables rather than literals because vendors rename models faster than a
 * release cycle (Bolum 5.4).
 *
 * @param chain       tier to the provider ids that serve it, in order
 * @param models      provider id to the model it should ask for
 * @param callTimeout per provider, not for the walk: a chain of three with a
 *                    30s timeout each is a 90s worst case, and that is the
 *                    intended trade against failing on one slow vendor
 * @param schemaRetries how many times a schema mismatch is retried on the same
 *                    provider before the walk stops. Bolum 27.3 says to retry
 *                    there rather than move on but does not say how often; one
 *                    retry catches a model that simply wandered, and more
 *                    would be paying repeatedly for a prompt that is wrong.
 */
@ConfigurationProperties(prefix = "atomcv.llm")
public record LlmProperties(
        Map<ModelTier, List<String>> chain,
        Map<String, String> models,
        Duration callTimeout,
        int schemaRetries) {

    public LlmProperties {
        chain = chain == null ? Map.of() : Map.copyOf(chain);
        models = models == null ? Map.of() : Map.copyOf(models);
        callTimeout = callTimeout == null ? Duration.ofSeconds(30) : callTimeout;
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
