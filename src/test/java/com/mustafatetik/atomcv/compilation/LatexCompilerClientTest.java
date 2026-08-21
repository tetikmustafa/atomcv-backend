package com.mustafatetik.atomcv.compilation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.shared.error.CompilationFailureKind;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The client's half of the contract with the compiler, against a stub that can
 * answer anything the real one can.
 */
class LatexCompilerClientTest {

    private HttpServer stub;
    private LatexCompilerClient client;
    private final AtomicReference<String> received = new AtomicReference<>();

    @BeforeEach
    void startStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress(0), 0);
        stub.start();
        client = new LatexCompilerClient(new CompilationProperties(
                "http://localhost:" + stub.getAddress().getPort(), Duration.ofSeconds(2)));
    }

    @AfterEach
    void stopStub() {
        stub.stop(0);
    }

    @Test
    void sendsTheSourceAndReturnsTheDocument() {
        respondWith("/compile", 200, "%PDF-1.7 pretend", 2);

        CompiledDocument document = client.compile("\\documentclass{article}...");

        assertThat(new String(document.pdf(), StandardCharsets.UTF_8)).startsWith("%PDF-");
        assertThat(document.pageCount()).isEqualTo(2);
        assertThat(received.get()).isEqualTo("\\documentclass{article}...");
    }

    /**
     * Faz F checks a page count it did not compute itself. A compiler that
     * does not report one is the wrong compiler, and answering with a document
     * of unknown length would break the one promise the product makes (P4).
     */
    @Test
    void aDocumentWithNoPageCountIsRefusedRatherThanGuessed() {
        respondWith("/compile", 200, "%PDF-1.7 pretend");

        assertThatThrownBy(() -> client.compile("x"))
                .isInstanceOf(CompilationException.class)
                .extracting(failure -> ((CompilationException) failure).kind())
                .isEqualTo(CompilationFailureKind.UNAVAILABLE);
    }

    @Test
    void turkishSourceSurvivesTheRoundTrip() {
        respondWith("/measure", 200, "ATOMCOST|var-1|10.0pt|0.0pt");

        client.measure("Merhaba dünya — İstanbul");

        assertThat(received.get()).isEqualTo("Merhaba dünya — İstanbul");
    }

    @Test
    void aRefusedDocumentCarriesItsLog() {
        respondWith("/compile", 422, "! Undefined control sequence.\\n l.3 \\\\nope");

        assertThatThrownBy(() -> client.compile("broken"))
                .isInstanceOf(CompilationException.class)
                .satisfies(failure -> {
                    var compilation = (CompilationException) failure;
                    assertThat(compilation.kind())
                            .isEqualTo(CompilationFailureKind.INVALID_DOCUMENT);
                    assertThat(compilation.log()).contains("Undefined control sequence");
                });
    }

    @Test
    void aBusyCompilerIsToldApartFromABrokenOne() {
        respondWith("/compile", 503, "busy");

        assertThatThrownBy(() -> client.compile("x"))
                .isInstanceOf(CompilationException.class)
                .extracting(failure -> ((CompilationException) failure).kind())
                .isEqualTo(CompilationFailureKind.BUSY);
    }

    @Test
    void anUnreachableCompilerIsAnOutageNotABadDocument() {
        var unreachable = new LatexCompilerClient(
                new CompilationProperties("http://localhost:1", Duration.ofSeconds(2)));

        assertThatThrownBy(() -> unreachable.compile("x"))
                .isInstanceOf(CompilationException.class)
                .extracting(failure -> ((CompilationException) failure).kind())
                .isEqualTo(CompilationFailureKind.UNAVAILABLE);
    }

    @Test
    void aCompilerThatNeverAnswersTimesOutRatherThanHangs() {
        stub.createContext("/compile", exchange -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        assertThatThrownBy(() -> client.compile("x"))
                .isInstanceOf(CompilationException.class)
                .extracting(failure -> ((CompilationException) failure).kind())
                .isEqualTo(CompilationFailureKind.TIMEOUT);
    }

    @Test
    void anUnexpectedStatusIsTreatedAsAnOutage() {
        respondWith("/compile", 500, "boom");

        assertThatThrownBy(() -> client.compile("x"))
                .isInstanceOf(CompilationException.class)
                .extracting(failure -> ((CompilationException) failure).kind())
                .isEqualTo(CompilationFailureKind.UNAVAILABLE);
    }

    private void respondWith(String path, int status, String body) {
        respondWith(path, status, body, 0);
    }

    private void respondWith(String path, int status, String body, int pages) {
        stub.createContext(path, exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            if (pages > 0) {
                exchange.getResponseHeaders().set("X-Page-Count", String.valueOf(pages));
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            exchange.close();
        });
    }
}
