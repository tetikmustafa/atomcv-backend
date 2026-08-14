package com.mustafatetik.atomcv.compilation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The LaTeX container, exercised for real (Bolum 29).
 *
 * <p>Tagged and excluded from {@code integrationTest}: the image is a couple of
 * gigabytes and takes minutes to build. Run it with {@code gradlew latexTest}
 * when anything under {@code docker/latex} changes.
 *
 * <p>What it proves is not that xelatex works — it is that the fence around it
 * does. A shell escape has to be refused, and it has to be refused by the
 * running container rather than by a flag someone believes is set.
 */
@Tag("latex")
@Testcontainers
class LatexContainerIT {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Container
    static final GenericContainer<?> LATEX = new GenericContainer<>(
            new ImageFromDockerfile("atomcv-latex-test", false)
                    .withFileFromPath(".", Path.of("docker/latex")))
            .withExposedPorts(8090)
            .withStartupTimeout(Duration.ofMinutes(5));

    @Test
    void compilesADocumentIntoAPdf() throws Exception {
        HttpResponse<byte[]> response = post("/compile", """
                \\documentclass{article}
                \\usepackage{fontspec}
                \\setmainfont{TeX Gyre Termes}
                \\begin{document}
                Merhaba dünya --- \\textbf{ETL} 300K+ satır
                \\end{document}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body(), 0, 5, StandardCharsets.ISO_8859_1))
                .as("a PDF, not an error page")
                .isEqualTo("%PDF-");
        // Turkish text and a real font, which is the check Stage 1 asks for.
        assertThat(response.body().length).isGreaterThan(1000);
    }

    /** Absolute rule 8, verified against the container rather than the flag. */
    @Test
    void refusesToRunAShellCommand() throws Exception {
        HttpResponse<byte[]> compiled = post("/compile", """
                \\documentclass{article}
                \\begin{document}
                \\immediate\\write18{touch /tmp/pwned}
                Attempted a shell escape.
                \\end{document}
                """);
        assertThat(compiled.statusCode()).isEqualTo(200);

        String log = text(post("/measure", """
                \\documentclass{article}
                \\begin{document}
                \\immediate\\write18{touch /tmp/pwned}
                Attempted a shell escape.
                \\end{document}
                """));
        assertThat(log).contains("runsystem(touch /tmp/pwned)...disabled");

        var probe = LATEX.execInContainer("ls", "/tmp/pwned");
        assertThat(probe.getExitCode()).as("the command never ran").isNotZero();
    }

    @Test
    void returnsTheLogForMeasurement() throws Exception {
        // Bolum 22.4's shape, with one correction: \mbox is already a LaTeX
        // command, so the box needs another name (EK D.7).
        String log = text(post("/measure", """
                \\documentclass{article}
                \\begin{document}
                \\newsavebox{\\measurebox}
                \\savebox{\\measurebox}{\\parbox{3in}{Built ETL pipelines processing 300K+ rows}}
                \\typeout{ATOMCOST|var-1|\\the\\ht\\measurebox|\\the\\dp\\measurebox}
                \\end{document}
                """));

        assertThat(log).contains("ATOMCOST|var-1|");
        assertThat(log).containsPattern("ATOMCOST\\|var-1\\|[0-9.]+pt\\|[0-9.]+pt");
    }

    @Test
    void aBrokenDocumentComesBackAsTheClientsProblemWithItsLog() throws Exception {
        HttpResponse<byte[]> response = post("/compile", """
                \\documentclass{article}
                \\begin{document}
                \\thisCommandDoesNotExist
                \\end{document}
                """);

        assertThat(response.statusCode()).as("422, not 500: the document is at fault")
                .isEqualTo(422);
        assertThat(text(response)).contains("Undefined control sequence");
    }

    @Test
    void runsAsAnUnprivilegedUserOnAReadOnlyFilesystem() throws Exception {
        assertThat(LATEX.execInContainer("id", "-u").getStdout().trim()).isEqualTo("1000");

        var write = LATEX.execInContainer("sh", "-c", "touch /opt/x");
        assertThat(write.getExitCode()).as("nothing outside /tmp is writable").isNotZero();
    }

    private static HttpResponse<byte[]> post(String path, String document)
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + LATEX.getHost() + ":" + LATEX.getMappedPort(8090) + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(document, StandardCharsets.UTF_8))
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String text(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }
}
