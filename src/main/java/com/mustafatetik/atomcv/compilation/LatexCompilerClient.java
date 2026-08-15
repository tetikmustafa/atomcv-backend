package com.mustafatetik.atomcv.compilation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The application's only way to reach the compiler (Bolum 29).
 *
 * <p>It sends LaTeX and gets back either a document or the log that explains
 * why not. Nothing else in the codebase talks to that container, which is what
 * keeps "user content is compiled somewhere isolated" a property of the system
 * rather than a habit.
 */
@Component
public class LatexCompilerClient {

    private static final Logger log = LoggerFactory.getLogger(LatexCompilerClient.class);

    private final HttpClient http;
    private final CompilationProperties properties;

    public LatexCompilerClient(CompilationProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** The PDF, or an exception carrying the log that explains its absence. */
    public CompiledDocument compile(String source) {
        HttpResponse<byte[]> response = send("/compile", source);
        int pages = response.headers().firstValue("X-Page-Count")
                .map(LatexCompilerClient::parsePageCount)
                .orElse(0);
        if (pages < 1) {
            // Faz F cannot promise a page limit it was unable to read, so a
            // compiler that does not report one is treated as the wrong
            // compiler rather than as a document with an unknown length (P4).
            throw failure(CompilationException.Kind.UNAVAILABLE,
                    "the compiler reported no page count", "", null);
        }
        return new CompiledDocument(response.body(), pages);
    }

    /**
     * The TeX log for a measurement run (Bolum 26.2). No document is produced
     * and none is wanted: what matters is what TeX said about the sizes.
     */
    public String measure(String source) {
        return new String(send("/measure", source).body(), StandardCharsets.UTF_8);
    }

    private HttpResponse<byte[]> send(String path, String source) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + path))
                .timeout(properties.requestTimeout())
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(source, StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException timeout) {
            throw failure(CompilationException.Kind.TIMEOUT, "compilation timed out", "", timeout);
        } catch (IOException unreachable) {
            throw failure(CompilationException.Kind.UNAVAILABLE,
                    "the compiler could not be reached", "", unreachable);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure(CompilationException.Kind.UNAVAILABLE,
                    "interrupted while compiling", "", interrupted);
        }

        return switch (response.statusCode()) {
            case 200 -> response;
            case 422 -> throw failure(CompilationException.Kind.INVALID_DOCUMENT,
                    "the document did not compile", body(response), null);
            case 503 -> throw failure(CompilationException.Kind.BUSY,
                    "every compilation slot is taken", "", null);
            default -> throw failure(CompilationException.Kind.UNAVAILABLE,
                    "the compiler answered " + response.statusCode(), "", null);
        };
    }

    private CompilationException failure(
            CompilationException.Kind kind, String message, String texLog, Throwable cause) {

        // The kind, never the log: it is built from the user's own content.
        log.warn("Compilation failed: {}", kind);
        return new CompilationException(kind, message, texLog, cause);
    }

    private static int parsePageCount(String header) {
        try {
            return Integer.parseInt(header.trim());
        } catch (NumberFormatException malformed) {
            return 0;
        }
    }

    private static String body(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }
}
