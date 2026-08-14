import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * The only thing allowed to run xelatex (Bolum 29).
 *
 * <p>LaTeX is a programming language: {@code \write18} runs shell commands and
 * {@code \input} reads files. User content reaches it, so this process is the
 * blast radius — it holds no credentials, sees no database, and its container
 * has no route to the internet.
 *
 * <p>Deliberately dependency-free. It is built from one source file with the
 * JDK's own HTTP server, so the image carries no library that could need
 * patching, and reading the whole thing takes a minute.
 */
public final class CompileServer {

    /** Bolum 29.5: a predictable ceiling, so a burst cannot starve Postgres. */
    private static final Semaphore SLOTS = new Semaphore(intEnv("LATEX_CONCURRENCY", 3));

    private static final int COMPILE_TIMEOUT_SECONDS = intEnv("LATEX_TIMEOUT_SECONDS", 20);
    private static final int QUEUE_TIMEOUT_SECONDS = intEnv("LATEX_QUEUE_TIMEOUT_SECONDS", 30);
    private static final long MAX_SOURCE_BYTES = intEnv("LATEX_MAX_SOURCE_BYTES", 2_000_000);

    private static final String MINIMAL_DOCUMENT =
            "\\documentclass{article}\\begin{document}warm\\end{document}";

    private CompileServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = intEnv("PORT", 8090);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/compile", exchange -> handle(exchange, Output.PDF));
        server.createContext("/measure", exchange -> handle(exchange, Output.LOG));
        server.createContext("/healthz", exchange -> respond(exchange, 200, "text/plain",
                "ok".getBytes(StandardCharsets.UTF_8)));

        server.setExecutor(Executors.newFixedThreadPool(intEnv("LATEX_THREADS", 8)));
        server.start();
        log("listening on " + port);

        warmUp();
    }

    /** What the caller wants back: the document, or what TeX said while making it. */
    private enum Output {
        PDF, LOG
    }

    private static void handle(HttpExchange exchange, Output output) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "text/plain", bytes("POST a LaTeX document"));
                return;
            }
            byte[] source = readAtMost(exchange.getRequestBody(), MAX_SOURCE_BYTES);
            if (source == null) {
                respond(exchange, 413, "text/plain", bytes("source too large"));
                return;
            }

            Result result = compileWithLimit(new String(source, StandardCharsets.UTF_8));
            if (output == Output.LOG) {
                respond(exchange, 200, "text/plain; charset=utf-8", bytes(result.log()));
            } else if (result.pdf() != null) {
                respond(exchange, 200, "application/pdf", result.pdf());
            } else {
                // 422: the document is the problem, not the service. The log is
                // what a developer needs and it is the caller's own content.
                respond(exchange, 422, "text/plain; charset=utf-8", bytes(result.log()));
            }
        } catch (QueueTimeout timeout) {
            respond(exchange, 503, "text/plain", bytes("busy"));
        } catch (Exception failure) {
            log("failed: " + failure);
            respond(exchange, 500, "text/plain", bytes("compilation failed"));
        } finally {
            exchange.close();
        }
    }

    private record Result(byte[] pdf, String log) {
    }

    private static final class QueueTimeout extends RuntimeException {
    }

    private static Result compileWithLimit(String source) throws Exception {
        boolean acquired;
        try {
            acquired = SLOTS.tryAcquire(QUEUE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new QueueTimeout();
        }
        if (!acquired) {
            throw new QueueTimeout();
        }
        try {
            return compile(source);
        } finally {
            SLOTS.release();
        }
    }

    /**
     * One job, one directory, and nothing left behind.
     *
     * <p>The environment is cleared rather than inherited: whatever the
     * container holds, a TeX document that manages to read it should find
     * nothing worth having.
     */
    private static Result compile(String source) throws IOException, InterruptedException {
        Path jobDir = Files.createTempDirectory(Path.of("/tmp"), "job-");
        try {
            Path document = jobDir.resolve("doc.tex");
            Files.writeString(document, source, StandardCharsets.UTF_8);

            ProcessBuilder builder = new ProcessBuilder(
                    // A wrapper that sets this compilation's rlimits and then
                    // becomes xelatex. Setting them on the service instead
                    // starved the JVM of its own heap.
                    System.getenv().getOrDefault("LATEX_COMMAND", "/opt/run-xelatex.sh"),
                    "-no-shell-escape",              // absolute rule 8
                    "-interaction=nonstopmode",
                    "-halt-on-error",
                    "-output-directory=" + jobDir,
                    document.toString());
            builder.directory(jobDir.toFile());
            builder.environment().clear();
            builder.environment().put("PATH", "/usr/bin:/bin");
            builder.environment().put("HOME", jobDir.toString());
            builder.environment().put("TEXMFVAR", jobDir.toString());
            builder.redirectErrorStream(true);

            Process process = builder.start();
            byte[] console = process.getInputStream().readAllBytes();
            if (!process.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(null, "timeout after " + COMPILE_TIMEOUT_SECONDS + "s");
            }

            String log = readLog(jobDir, console);
            Path pdf = jobDir.resolve("doc.pdf");
            return new Result(Files.exists(pdf) ? Files.readAllBytes(pdf) : null, log);
        } finally {
            deleteRecursively(jobDir);
        }
    }

    /**
     * The log file where there is one, the console otherwise. A failing run
     * often writes neither cleanly, and having the console as a fallback is
     * the difference between a diagnosable failure and a blank one.
     */
    private static String readLog(Path jobDir, byte[] console) throws IOException {
        Path logFile = jobDir.resolve("doc.log");
        if (Files.exists(logFile)) {
            return Files.readString(logFile, StandardCharsets.UTF_8);
        }
        return new String(console, StandardCharsets.UTF_8);
    }

    private static void warmUp() {
        // Bolum 29.6: the first run builds font caches and takes seconds.
        // Paying that here means no user ever pays it.
        try {
            long started = System.currentTimeMillis();
            compileWithLimit(MINIMAL_DOCUMENT);
            log("warm-up finished in " + (System.currentTimeMillis() - started) + "ms");
        } catch (Exception failure) {
            log("warm-up failed: " + failure);
        }
    }

    private static byte[] readAtMost(InputStream input, long limit) throws IOException {
        byte[] content = input.readNBytes((int) limit + 1);
        return content.length > limit ? null : content;
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void deleteRecursively(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort: the container's /tmp is a tmpfs and goes
                    // away with the process anyway.
                }
            });
        } catch (IOException ignored) {
            // Same.
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException malformed) {
            return fallback;
        }
    }

    private static void log(String message) {
        System.out.println("[latex] " + message);
    }
}
