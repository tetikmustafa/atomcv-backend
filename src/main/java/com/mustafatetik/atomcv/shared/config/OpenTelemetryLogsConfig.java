package com.mustafatetik.atomcv.shared.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connects logback's {@code OTEL} appender to the SDK Boot configured.
 *
 * <p>The appender is declared in {@code logback-spring.xml}, which logback
 * reads before there is a Spring context, so it cannot find the SDK by itself:
 * it drops events until this hands it one. Boot builds that {@code
 * OpenTelemetry} from {@code management.otlp.logging.*}, and with export off —
 * every local run and the whole suite — the SDK has no exporter and the lines
 * go nowhere but the console.
 */
@Configuration
public class OpenTelemetryLogsConfig {

    @Bean
    InitializingBean openTelemetryLogAppender(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }
}
