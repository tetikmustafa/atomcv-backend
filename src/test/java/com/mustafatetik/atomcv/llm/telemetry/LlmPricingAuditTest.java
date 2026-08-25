package com.mustafatetik.atomcv.llm.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.llm.gateway.LlmProperties;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * F-015 — the price table and the chain drifting apart.
 *
 * <p>The table named a model that had been retired while every chain ran a
 * different one, so {@code costOf} answered zero for every call and the only
 * sign was a meter nobody was watching. Whether the two agree is knowable
 * before the first call, which is when it is cheap to fix.
 */
class LlmPricingAuditTest {

    @Test
    void amodelWithNoPriceIsNamed() {
        var audit = audit(Map.of("openrouter", "vendor/retired-model"),
                Map.of("vendor/current-model", free()));

        assertThat(audit.unpricedModels()).containsExactly("openrouter=vendor/retired-model");
    }

    @Test
    void apricedModelIsNotNamed() {
        var audit = audit(Map.of("openrouter", "vendor/current-model"),
                Map.of("vendor/current-model", free()));

        assertThat(audit.unpricedModels()).isEmpty();
    }

    /**
     * A free model is priced at zero explicitly. {@code costOf} would answer
     * zero either way; the audit is what tells the two apart, and it has to
     * stay quiet about the one that was decided on purpose.
     */
    @Test
    void afreeModelPricedAtZeroCountsAsPriced() {
        var audit = audit(Map.of("openrouter", "google/gemma-4-26b-a4b-it:free"),
                Map.of("google/gemma-4-26b-a4b-it:free", free()));

        assertThat(audit.unpricedModels()).isEmpty();
    }

    /**
     * Bolum 27.3 makes a provider with no model unavailable and never calls
     * it, so it is not a gap in the table — warning about it would train the
     * operator to skip the line.
     */
    @Test
    void aproviderWithNoModelIsNotAGapInTheTable() {
        var audit = audit(Map.of("openrouter", "", "gemini", "   "), Map.of());

        assertThat(audit.unpricedModels()).isEmpty();
    }

    /** Absolute rule 7: the lookup is case-folded with Locale.ROOT. */
    @Test
    void thelookupIgnoresCaseTheWayThePriceTableDoes() {
        var audit = audit(Map.of("openrouter", "Vendor/Current-Model"),
                Map.of("vendor/current-model", free()));

        assertThat(audit.unpricedModels()).isEmpty();
    }

    private static LlmPricingAudit audit(
            Map<String, String> models, Map<String, LlmPricing.ModelPrice> prices) {

        return new LlmPricingAudit(
                new LlmProperties(Map.of(ModelTier.CHEAP, List.of("openrouter")),
                        models, Duration.ofSeconds(30), 1),
                new LlmPricing(prices));
    }

    private static LlmPricing.ModelPrice free() {
        return new LlmPricing.ModelPrice(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
