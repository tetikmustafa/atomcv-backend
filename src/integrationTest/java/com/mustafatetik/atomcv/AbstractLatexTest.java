package com.mustafatetik.atomcv;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
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
 *
 * <p><strong>And the fake compiler is off here, which is the whole reason it
 * is a property.</strong> Four of these classes run under {@code local-fake},
 * because they want the fake <em>model</em> — the run costs nothing and
 * replays fixtures — and the real compiler, which is the thing they are about.
 * Gated on the profile alone, the fake took the compiler from them too: the
 * assertions that a real one-page PDF is more than two thousand bytes met a
 * 669-byte placeholder. Declared here rather than on each class so a fifth
 * cannot be written without it.
 */
@TestPropertySource(properties = "atomcv.latex.fake=false")
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
