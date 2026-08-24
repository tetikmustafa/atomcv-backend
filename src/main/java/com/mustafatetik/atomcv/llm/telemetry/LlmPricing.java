package com.mustafatetik.atomcv.llm.telemetry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What a call costs (Bolum 27.4).
 *
 * <p>Configuration and not code, for the same reason the model names are:
 * vendors change prices faster than a release cycle, and a deployment should
 * be able to correct a wrong figure without one.
 *
 * <p><strong>Cached input is priced apart.</strong> Bolum 27.4 says a cached
 * token costs a fraction of a fresh one, and a cost computed without that
 * overstates every call that hit a cache — which is most of them, and would
 * make the budget brake fire on a bill nobody is paying.
 *
 * <p>An unpriced model costs <strong>zero</strong>, not a guess. A made-up
 * figure would put invented money into a number an operator is meant to act
 * on; a zero is visibly wrong, and the counter beside it says how many calls
 * went unpriced.
 *
 * @param perMillionTokens model name to its three prices, in USD per million
 */
@ConfigurationProperties(prefix = "atomcv.llm.pricing")
public record LlmPricing(Map<String, ModelPrice> perMillionTokens) {

    /** USD per million tokens, which is how every vendor quotes them. */
    public record ModelPrice(BigDecimal input, BigDecimal output, BigDecimal cached) {

        public ModelPrice {
            input = input == null ? BigDecimal.ZERO : input;
            output = output == null ? BigDecimal.ZERO : output;
            // Absent means "same as fresh input", which is the conservative
            // reading: a vendor with no cache discount charges full price.
            cached = cached == null ? input : cached;
        }
    }

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    public LlmPricing {
        perMillionTokens = perMillionTokens == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(perMillionTokens));
    }

    /** Whether a figure for this model exists at all. Watched, not assumed. */
    public boolean knows(String model) {
        return model != null && perMillionTokens.containsKey(key(model));
    }

    /**
     * @return the cost in USD, at the scale {@code cost_usd} stores
     *         ({@code NUMERIC(10,6)}). Rounded half-up, because truncating
     *         every call towards zero makes a day's total quietly low.
     */
    public BigDecimal costOf(String model, int inputTokens, int outputTokens, int cachedTokens) {
        ModelPrice price = perMillionTokens.get(key(model));
        if (price == null) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        // Cached tokens are part of the input count on every vendor's bill, so
        // they are priced instead of it, not on top of it.
        int freshInput = Math.max(0, inputTokens - cachedTokens);
        return price.input().multiply(BigDecimal.valueOf(freshInput))
                .add(price.cached().multiply(BigDecimal.valueOf(cachedTokens)))
                .add(price.output().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    /** Locale.ROOT: absolute rule 7, and model names come from configuration. */
    private static String key(String model) {
        return model.toLowerCase(Locale.ROOT);
    }
}
