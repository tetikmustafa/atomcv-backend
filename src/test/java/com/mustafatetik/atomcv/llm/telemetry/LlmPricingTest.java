package com.mustafatetik.atomcv.llm.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** What a call costs (Bolum 27.4), and what an unpriced one costs. */
class LlmPricingTest {

    /** $0.10 in, $0.40 out, $0.025 cached — per million. */
    private static final LlmPricing PRICING = new LlmPricing(Map.of(
            "gemini-flash", new LlmPricing.ModelPrice(
                    new BigDecimal("0.10"), new BigDecimal("0.40"), new BigDecimal("0.025"))));

    @Test
    void afreshCallIsInputPlusOutput() {
        // 1M in at 0.10 + 0.5M out at 0.40 = 0.10 + 0.20
        assertThat(PRICING.costOf("gemini-flash", 1_000_000, 500_000, 0))
                .isEqualByComparingTo("0.300000");
    }

    /**
     * Bolum 27.4: a cached token costs a fraction of a fresh one. Counting it
     * at full price overstates every call that hit a cache — most of them —
     * and would fire the budget brake on a bill nobody is paying.
     */
    @Test
    void cachedInputIsPricedApartAndNotOnTop() {
        // 1M input of which 800K cached: 200K at 0.10 + 800K at 0.025.
        assertThat(PRICING.costOf("gemini-flash", 1_000_000, 0, 800_000))
                .isEqualByComparingTo("0.040000");
        assertThat(PRICING.costOf("gemini-flash", 1_000_000, 0, 800_000))
                .isLessThan(PRICING.costOf("gemini-flash", 1_000_000, 0, 0));
    }

    /**
     * An unpriced model costs zero, not a guess: invented money in a number an
     * operator acts on is worse than a visible gap, and the recorder counts
     * these separately.
     */
    @Test
    void anunpricedModelCostsZeroAndSaysSo() {
        assertThat(PRICING.costOf("something-nobody-priced", 1_000_000, 1_000_000, 0))
                .isEqualByComparingTo("0");
        assertThat(PRICING.knows("something-nobody-priced")).isFalse();
        assertThat(PRICING.knows("gemini-flash")).isTrue();
    }

    /** Absent cache pricing means full price, which is the conservative reading. */
    @Test
    void amodelWithNoCacheDiscountChargesFullInput() {
        var noDiscount = new LlmPricing(Map.of("plain",
                new LlmPricing.ModelPrice(new BigDecimal("1.00"), BigDecimal.ZERO, null)));

        assertThat(noDiscount.costOf("plain", 1_000_000, 0, 1_000_000))
                .isEqualByComparingTo("1.000000");
    }

    /** The column is NUMERIC(10,6); rounding half-up keeps a day's total honest. */
    @Test
    void thescaleMatchesTheColumn() {
        assertThat(PRICING.costOf("gemini-flash", 1, 0, 0).scale()).isEqualTo(6);
        assertThat(PRICING.costOf("unpriced", 1, 0, 0).scale()).isEqualTo(6);
    }

    /** Absolute rule 7: a Turkish locale lowercases "I" to a dotless one. */
    @Test
    void modellookupSurvivesATurkishDefaultLocale() {
        var pricing = new LlmPricing(Map.of("mini",
                new LlmPricing.ModelPrice(BigDecimal.ONE, BigDecimal.ZERO, null)));
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(pricing.knows("MINI")).isTrue();
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void nopricesAtAllIsNotAFailure() {
        var empty = new LlmPricing(null);

        assertThat(empty.costOf("anything", 100, 100, 0)).isEqualByComparingTo("0");
    }
}
