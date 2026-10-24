package com.mustafatetik.atomcv.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.springframework.boot.actuate.autoconfigure.logging.OpenTelemetryLoggingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.logging.otlp.OtlpLoggingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The log lines reach Axiom when, and only when, somewhere to send is given
 * (EK C.1 "Axiom log akışı").
 *
 * <p>Off is the half that matters most day to day: an exporter with nowhere
 * to send retries on a timer and logs its own failure, which is observability
 * making the system worse — the reason the metrics exporter is off by default
 * too ({@code ObservabilityIT}).
 */
class OpenTelemetryLogsConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OpenTelemetryAutoConfiguration.class,
                    OpenTelemetryLoggingAutoConfiguration.class,
                    OtlpLoggingAutoConfiguration.class))
            .withUserConfiguration(OpenTelemetryLogsConfig.class);

    @Test
    void nothingIsExportedUntilAUrlIsGiven() {
        runner.withPropertyValues("management.otlp.logging.export.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LogRecordExporter.class));
    }

    /**
     * Its own switch, never the metrics one. The exporter refuses an empty
     * endpoint at start-up, so a shared switch meant a deployment with metrics
     * on and no logs URL did not boot — which a developer's `.env` showed the
     * first time `make openapi` ran after this landed.
     */
    @Test
    @SuppressWarnings("unchecked")
    void logsAreSwitchedOnByTheirOwnVariable() throws java.io.IOException {
        try (var yaml = getClass().getResourceAsStream("/application.yml")) {
            Map<String, Object> document = new Yaml().load(yaml);
            var management = (Map<String, Object>) document.get("management");
            var otlp = (Map<String, Object>) management.get("otlp");
            var logging = (Map<String, Object>) otlp.get("logging");
            var export = (Map<String, Object>) logging.get("export");

            assertThat(String.valueOf(export.get("enabled"))).isEqualTo("${OTLP_LOGS_ENABLED:false}");
        }
    }

    @Test
    void aUrlAndTheSwitchExportTheLines() {
        runner.withPropertyValues(
                        "management.otlp.logging.export.enabled=true",
                        "management.otlp.logging.endpoint=https://api.axiom.co/v1/logs",
                        "management.otlp.logging.headers.X-Axiom-Dataset=atomcv")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LogRecordExporter.class);
                });
    }
}
