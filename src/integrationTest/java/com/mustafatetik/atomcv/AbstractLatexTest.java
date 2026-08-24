package com.mustafatetik.atomcv;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * One LaTeX container for every test that needs a real compiler.
 *
 * <p>Extracted when the second such test arrived. Two classes each declaring
 * their own would each hold a container, and the image takes minutes to build
 * — the {@code false} tells Testcontainers to reuse one already built, which
 * only helps if there is one name rather than two.
 *
 * <p>Started in a static block and never stopped, the singleton pattern
 * Testcontainers documents: {@code @Container} would stop it after the first
 * class while Spring's cached context still pointed at the port. Ryuk removes
 * it when the JVM exits.
 */
public abstract class AbstractLatexTest extends AbstractIntegrationTest {

    static final GenericContainer<?> LATEX = new GenericContainer<>(
            new ImageFromDockerfile("atomcv-latex-test", false)
                    .withFileFromPath(".", Path.of("docker/latex")))
            .withExposedPorts(8090)
            .withStartupTimeout(Duration.ofMinutes(5));

    static {
        LATEX.start();
    }

    @DynamicPropertySource
    static void latexAddress(DynamicPropertyRegistry registry) {
        registry.add("atomcv.latex.base-url",
                () -> "http://" + LATEX.getHost() + ":" + LATEX.getMappedPort(8090));
    }
}
