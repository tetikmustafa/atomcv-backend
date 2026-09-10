package com.mustafatetik.atomcv.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code performance-budgets.yaml}, read (Bolum 52.6).
 *
 * <p>A file rather than numbers in assertions, because the specification asks
 * for exactly that: a budget somebody wants to raise has to raise it in a file,
 * where the change is a line in a pull request instead of a number quietly
 * edited next to the test that was failing.
 *
 * <p>Test-side only. Nothing in production reads a budget — these are a guard
 * against a change, not a limit the product enforces.
 */
public final class PerformanceBudgets {

    private static final JsonNode ROOT = load();

    private PerformanceBudgets() {
    }

    private static JsonNode load() {
        // From the repository root, which is where Gradle runs a test from.
        Path file = Path.of("performance-budgets.yaml");
        try {
            return new ObjectMapper(new YAMLFactory()).readTree(Files.readString(file));
        } catch (IOException missing) {
            // Not a skipped test: a budget file that cannot be read is a guard
            // nobody is being held to, and passing without it would be worse
            // than failing.
            throw new UncheckedIOException(
                    "performance-budgets.yaml is what these tests are held to", missing);
        }
    }

    public static long backendP95Millis(String operation) {
        return required("backend", operation, "p95_ms").asLong();
    }

    public static long backendP50Millis(String operation) {
        return required("backend", operation, "p50_ms").asLong();
    }

    public static int maxQueriesForProfileLoad() {
        return required("queries", "profile_load_max").asInt();
    }

    public static double maxGrowthWhenInputDoubles() {
        return required("scaling", "max_growth_when_input_doubles").asDouble();
    }

    /**
     * @throws IllegalStateException for a budget the file does not set. A test
     *         holding something to a missing number would hold it to zero or
     *         to infinity, and both are worse than saying the file is
     *         incomplete.
     */
    private static JsonNode required(String... path) {
        JsonNode node = ROOT;
        for (String step : path) {
            node = node.path(step);
        }
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalStateException(
                    "performance-budgets.yaml sets no " + String.join(".", path));
        }
        return node;
    }
}
