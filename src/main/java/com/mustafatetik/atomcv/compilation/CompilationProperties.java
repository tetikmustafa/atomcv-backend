package com.mustafatetik.atomcv.compilation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the compiler is and how long it is given.
 *
 * @param baseUrl        the container's address. In development the port is
 *                       published; in production it is a name on the isolated
 *                       network (Bolum 29.3).
 * @param requestTimeout longer than the container's own compile timeout, so a
 *                       document that runs long comes back as TeX's answer
 *                       rather than as a dropped connection
 */
@ConfigurationProperties(prefix = "atomcv.latex")
public record CompilationProperties(String baseUrl, Duration requestTimeout) {

    public CompilationProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8090" : baseUrl;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(45) : requestTimeout;
    }
}
