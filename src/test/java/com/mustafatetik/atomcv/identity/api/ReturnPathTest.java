package com.mustafatetik.atomcv.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The open-redirect guard, watched refusing. Every vector here is one that a
 * plain "does it start with a slash" check waves straight through.
 */
class ReturnPathTest {

    @ParameterizedTest
    @ValueSource(strings = {
            // Protocol-relative: a browser resolves it as absolute, and it
            // does begin with a slash.
            "//evil.example",
            "//evil.example/login",
            // Some browsers normalise a backslash to a slash, which turns this
            // into the case above after the check has already passed.
            "/\\evil.example",
            "\\\\evil.example",
            // Any scheme at all.
            "https://evil.example",
            "javascript:alert(1)",
            "/redirect?to=https://evil.example",
            // Not a path to begin with.
            "evil.example",
            "../admin",
    })
    void anythingThatIsNotAPlainPathOnThisSiteBecomesTheRoot(String hostile) {
        assertThat(ReturnPath.of(hostile)).isEqualTo("/");
    }

    @Test
    void absentOrEmptyIsTheRoot() {
        assertThat(ReturnPath.of(null)).isEqualTo("/");
        assertThat(ReturnPath.of("")).isEqualTo("/");
        assertThat(ReturnPath.of("   ")).isEqualTo("/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/profile", "/generate/new", "/profile?tab=atoms"})
    void aPlainPathIsKept(String path) {
        assertThat(ReturnPath.of(path)).isEqualTo(path);
    }
}
