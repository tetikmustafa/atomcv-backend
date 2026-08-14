package com.mustafatetik.atomcv.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The published API document (Bolum XI-B.9.1).
 *
 * <p>This is how the two repositories stay in agreement: the frontend runs
 * {@code npm run gen:api} against it and its TypeScript stops compiling when
 * the contract moves. That only works if the document carries enums and
 * headers rather than happy-path payloads alone.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI atomCvOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AtomCV API")
                .version("v1")
                .description("""
                        Errors carry a translatable `code`, the `params` its message needs, \
                        and the `resolutions` the user can act on. The server never sends \
                        display text — see the error catalogue in the architecture document.""")
                .license(new License().name("MIT")));
    }
}
