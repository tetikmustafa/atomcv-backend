package com.mustafatetik.atomcv.shared.config;

import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson modules that are not registered by Spring Boot on their own.
 *
 * <p>Without {@code JsonNullableModule}, a patch field would arrive as an
 * object with a {@code present} flag rather than as the value itself, and the
 * distinction between "not sent" and "sent as null" — the whole point of the
 * type — would never reach the service.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonNullableModule jsonNullableModule() {
        return new JsonNullableModule();
    }
}
