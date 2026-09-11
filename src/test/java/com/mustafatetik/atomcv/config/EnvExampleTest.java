package com.mustafatetik.atomcv.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * {@code .env.example} against what the application actually reads.
 *
 * <p><strong>Written because it had already drifted.</strong> The example
 * offered {@code DAILY_BUDGET_USD}, four places in the specification named it,
 * and the release checklist had a box for setting it — while the code read
 * {@code ANOMALY_DAILY_BUDGET_USD}. Somebody setting a spending limit was
 * setting nothing, and the only reason that was survivable is that the
 * default happened to be lower than the number they would have typed.
 *
 * <p>Two rules, and they fail for opposite reasons:
 *
 * <ul>
 * <li><strong>Nothing documented is dead.</strong> A name in the example that
 * no configuration reads is a knob connected to nothing, and it is worse than
 * a missing one: a missing knob is noticed the first time somebody looks for
 * it, and a dead one is never noticed at all.</li>
 * <li><strong>Nothing required is missing.</strong> A placeholder written
 * without a default — {@code ${FOO}} rather than {@code ${FOO:bar}} — is one
 * the application will not start without, so it has to be in the file a person
 * copies. The tunables with defaults are deliberately not required here;
 * documenting all seventy-seven would turn the example into the reference and
 * stop anyone reading it.</li>
 * </ul>
 */
class EnvExampleTest {

    /** From the repository root, which is where Gradle runs a test from. */
    private static final Path EXAMPLE = Path.of(".env.example");
    private static final Path CONFIG = Path.of("src/main/resources");

    /** {@code ${NAME}} or {@code ${NAME:default}}, capturing which it was. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)(:?)");

    /** {@code NAME=value}, commented out or not. */
    private static final Pattern ASSIGNMENT = Pattern.compile("(?m)^\\s*#?\\s*([A-Z][A-Z0-9_]*)=");

    @Test
    void everyNameInTheExampleIsReadBySomething() {
        Set<String> read = placeholders(false);
        read.addAll(placeholders(true));

        assertThat(documented())
                .as("a name here that nothing reads is a knob wired to nothing")
                .isSubsetOf(read);
    }

    @Test
    void everyNameTheApplicationCannotStartWithoutIsInTheExample() {
        assertThat(placeholders(false))
                .as("a placeholder with no default is required, so it has to be copyable")
                .isSubsetOf(documented());
    }

    /**
     * And the file is not empty, which is the assertion that keeps the two
     * above from passing on a file that failed to load.
     */
    @Test
    void theExampleWasActuallyRead() {
        assertThat(documented()).hasSizeGreaterThan(10);
        assertThat(placeholders(true)).hasSizeGreaterThan(10);
    }

    private static Set<String> documented() {
        return names(ASSIGNMENT, read(EXAMPLE));
    }

    /** @param withDefault true for {@code ${FOO:bar}}, false for {@code ${FOO}} */
    private static Set<String> placeholders(boolean withDefault) {
        var found = new LinkedHashSet<String>();
        try (Stream<Path> files = Files.list(CONFIG)) {
            files.filter(path -> path.getFileName().toString().startsWith("application"))
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .map(EnvExampleTest::read)
                    .forEach(content -> {
                        Matcher matcher = PLACEHOLDER.matcher(content);
                        while (matcher.find()) {
                            if (matcher.group(2).equals(":") == withDefault) {
                                found.add(matcher.group(1));
                            }
                        }
                    });
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Cannot list " + CONFIG, unreadable);
        }
        return found;
    }

    private static Set<String> names(Pattern pattern, String content) {
        var found = new LinkedHashSet<String>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException unreadable) {
            // Not a skipped test: a file that cannot be read is a rule nobody
            // is being held to, and passing without it is worse than failing.
            throw new UncheckedIOException(path + " is what this test is about", unreadable);
        }
    }
}
