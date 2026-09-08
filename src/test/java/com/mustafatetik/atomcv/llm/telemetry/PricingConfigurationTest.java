package com.mustafatetik.atomcv.llm.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.llm.gateway.LlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * The price table as the application actually reads it (Bolum 27.4).
 *
 * <p>{@link LlmPricingTest} checks the arithmetic against a table written for
 * the purpose. This checks the table in {@code application.yml}, because the
 * failure this exists for is not an arithmetic one: F-015 was a table that
 * priced a model no chain ran, and every cost in the system was zero while the
 * code was correct. A hand-made map cannot fail that way.
 *
 * <p>The figures come from OpenRouter's endpoints API for the slug in use, and
 * they carry a promotional discount — so this is also where a stale table shows
 * up as a number that no longer matches the vendor.
 */
class PricingConfigurationTest {

    /** What every chain runs today. */
    private static final String MODEL = "openai/gpt-5.6-sol";

    @Configuration
    @EnableConfigurationProperties({LlmPricing.class, LlmProperties.class})
    static class Binding {
    }

    @Test
    void themodelInUseIsPriced() {
        run(pricing -> assertThat(pricing.knows(MODEL))
                .as("an unpriced model costs zero, and zero is not free")
                .isTrue());
    }

    /**
     * 2 in, 10 out, 0.2 cached, per million — the standard OpenAI endpoint's
     * figures. Asserted through a call the size of a real extraction rather
     * than through the three numbers, so the test says what the money is.
     */
    @Test
    void awholeCvExtractionCostsSevenCents() {
        // 15,000 input at 2/M = 0.03; 4,000 output at 10/M = 0.04.
        run(pricing -> assertThat(pricing.costOf(MODEL, 15_000, 4_000, 0))
                .isEqualByComparingTo("0.070000"));
    }

    /** And a cached prefix is the discount it is supposed to be. */
    @Test
    void acachedPrefixCostsATenthOfAFreshOne() {
        run(pricing -> {
            var cached = pricing.costOf(MODEL, 15_000, 4_000, 12_000);
            var fresh = pricing.costOf(MODEL, 15_000, 4_000, 0);
            // 3,000 fresh at 2/M + 12,000 cached at 0.2/M + output unchanged.
            assertThat(cached).isEqualByComparingTo("0.048400");
            assertThat(cached).isLessThan(fresh);
        });
    }

    /**
     * The audit stays silent for the model the deployment names, which is the
     * whole point of the entry: it is the startup line that would otherwise
     * say every cost is about to be zero.
     */
    @Test
    void thestartupAuditHasNothingToReportForIt() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("OPENROUTER_MODEL=" + MODEL)
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(Binding.class)
                .run(context -> {
                    var audit = new LlmPricingAudit(
                            context.getBean(LlmProperties.class),
                            context.getBean(LlmPricing.class));
                    assertThat(audit.unpricedModels()).isEmpty();
                });
    }

    private static void run(java.util.function.Consumer<LlmPricing> assertion) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(Binding.class)
                .run(context -> assertion.accept(context.getBean(LlmPricing.class)));
    }
}
