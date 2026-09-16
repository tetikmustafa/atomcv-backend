package com.mustafatetik.atomcv.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every figure in the budget file has something holding it to it.
 *
 * <p><strong>Three did not</strong> (denetim, beşinci tur). The file's own
 * header says "read by tests"; {@code phase_scoring} had no test anywhere,
 * {@code profile_load}'s milliseconds had none either, and
 * {@code backendP50Millis} was an accessor with no caller in the repository.
 * Nothing failed, because nothing was checking — a budget nobody asserts
 * fails in exactly one way, silently, and the first person to learn it is
 * whoever goes looking.
 *
 * <p><strong>Two shapes, so two assertions.</strong> A budget can be
 * unenforced by being named in the file and read by nobody, or by having a
 * reader that nobody calls. The first is the one that happened and the second
 * is how the first hides: an accessor makes a key look wired.
 */
class BudgetsAreHeldTest {

    private static final Path FILE = Path.of("performance-budgets.yaml");

    private static final Path TESTS = Path.of("src/test/java");

    private static final Path INTEGRATION_TESTS = Path.of("src/integrationTest/java");

    /** This class names every key and accessor, so it must not count itself. */
    private static final String SELF = "BudgetsAreHeldTest.java";

    @Test
    void everyBackendOperationIsNamedByAtest() {
        JsonNode backend = read().path("backend");

        assertThat(backend.fieldNames())
                .toIterable()
                .as("a budget in the file that no test asks for")
                .allSatisfy(operation -> assertThat(sources())
                        .as("operation '%s'", operation)
                        .anySatisfy(source ->
                                assertThat(source).contains('"' + operation + '"')));
    }

    /**
     * And every way of reading the file has a caller. An accessor with none is
     * how {@code p50_ms} sat in the file looking enforced: the key was there,
     * a method returned it, and the two of them together proved nothing.
     */
    @Test
    void everyAccessorIsCalled() {
        assertThat(publicAccessors())
                .as("nothing calls this; either hold a budget with it or drop it")
                .allSatisfy(accessor -> assertThat(sources())
                        .as("accessor '%s'", accessor)
                        .anySatisfy(source ->
                                assertThat(source).contains(accessor + "(")));
    }

    /** The assertion that keeps both above from passing on an empty read. */
    @Test
    void bothSidesWereActuallyRead() {
        assertThat(read().path("backend")).isNotEmpty();
        assertThat(publicAccessors()).hasSizeGreaterThan(1);
        assertThat(sources()).hasSizeGreaterThan(100);
    }

    private static List<String> publicAccessors() {
        return Stream.of(PerformanceBudgets.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .toList();
    }

    /**
     * Read as text rather than reflected over: what is being asked is whether
     * somebody wrote the name down, and a compiled class has lost the string
     * literal that names an operation.
     */
    private static List<String> sources() {
        var sources = new ArrayList<String>();
        for (Path root : List.of(TESTS, INTEGRATION_TESTS)) {
            try (Stream<Path> tree = Files.walk(root)) {
                tree.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.getFileName().toString().equals(SELF))
                        .forEach(path -> sources.add(read(path)));
            } catch (IOException unreadable) {
                throw new UncheckedIOException("the test sources are the other side", unreadable);
            }
        }
        return sources;
    }

    private static JsonNode read() {
        try {
            return new ObjectMapper(new YAMLFactory()).readTree(read(FILE));
        } catch (IOException malformed) {
            throw new UncheckedIOException("performance-budgets.yaml", malformed);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException missing) {
            throw new UncheckedIOException(path.toString(), missing);
        }
    }
}
