package com.mustafatetik.atomcv;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * What the deployment publishes about itself (Bolum 44.3, Bolum 2).
 *
 * <p><strong>The exporter is off unless a URL is given, and that is what is
 * asserted here.</strong> An OTLP registry with nowhere to send retries on a
 * timer and fills the log with its own failures — observability making the
 * system worse — so every developer machine and every test run would pay for a
 * feature none of them use.
 *
 * <p>What cannot be asserted here is the other half: that the metrics arrive
 * in Axiom. The dataset is created in Adim 3.1, so the Stage 2 checklist's
 * "logs visible in Axiom" is not tickable yet and the build guide now says so.
 * Wiring it now means Stage 3 creates a dataset rather than also writing code.
 */
class ObservabilityIT extends AbstractIntegrationTest {

    @Autowired
    private MeterRegistry meters;

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    void theexporterIsOffUntilSomewhereToSendIsConfigured() {
        assertThat(context.getBeanNamesForType(
                io.micrometer.registry.otlp.OtlpMeterRegistry.class))
                .as("an exporter with nowhere to send retries on a timer and logs its"
                        + " own failure — observability making the system worse")
                .isEmpty();
    }

    /**
     * Counters keep working with no exporter attached: the brake and the
     * anomaly pass both read them, and they must not depend on anybody
     * watching.
     */
    @Test
    void metersStillWorkWithNothingExporting() {
        meters.counter("test.probe").increment();

        assertThat(meters.find("test.probe").counter()).isNotNull();
    }

    /**
     * Every deployment's numbers arriving in one undifferentiated stream makes
     * "is this staging?" unanswerable, which is the first question anyone asks
     * of a graph.
     */
    @Test
    void everyMetricIsTaggedWithTheApplicationAndEnvironment() {
        meters.counter("test.tagged").increment();

        var counter = meters.find("test.tagged").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getTag("application")).isEqualTo("atomcv-backend");
        assertThat(counter.getId().getTag("environment")).isNotBlank();
    }
}
