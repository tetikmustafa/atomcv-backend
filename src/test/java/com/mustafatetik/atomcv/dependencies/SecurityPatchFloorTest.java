package com.mustafatetik.atomcv.dependencies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The three libraries this build takes a security patch for, checked where it
 * counts: at runtime, on the classpath that actually ships.
 *
 * <p><strong>Why a test and not the comment in {@code build.gradle.kts}.</strong>
 * Each of the three is an {@code extra["...version"]} property overriding Spring
 * Boot's BOM, and all three arrive transitively — Netty through the mail and
 * Redis clients, pgjdbc through the driver, Tomcat through the web starter. Two
 * things can quietly undo that, and until now neither had a signal:
 *
 * <ul>
 *   <li>An override that stops taking effect. A starter that pins its own
 *       version, a property renamed by a Boot upgrade, or a typo in the
 *       property name all leave a green build shipping the vulnerable
 *       version.</li>
 *   <li>The image scan that used to catch it. Trivy fails the Deploy workflow
 *       on HIGH, and Deploy is hand-run only until there is a VPS — so nothing
 *       in CI looks at these versions at all.</li>
 * </ul>
 *
 * <p><strong>A floor, not an equality.</strong> These assertions pass on any
 * version at or above the fix, so the day Boot's BOM catches up the override can
 * be deleted and this test stays green. That is the direction it has to fail in:
 * loud when the version goes backwards, silent when it goes forwards.
 *
 * <p><strong>What this does not do.</strong> It does not know about CVEs
 * published after it was written. Nothing here notices those either —
 * Dependabot cannot see a Gradle {@code extra} property, which is why it has
 * bumped five declared dependencies in this repository and never these three.
 * Reading the versions is a hand check; this only holds the ground already won.
 */
class SecurityPatchFloorTest {

    /** CVE-2026-54291, the SCRAM downgrade. Boot 3.5.16's BOM pins 42.7.11. */
    private static final String PGJDBC_FIX = "42.7.12";

    /** CVE-2026-59901, the decoder loop. Boot 3.5.16's BOM pins 4.1.135.Final. */
    private static final String NETTY_FIX = "4.1.136";

    /** CVE-2026-65182, -65905 and -68525. Boot 3.5.16's BOM pins 10.1.55. */
    private static final String TOMCAT_FIX = "10.1.59";

    @Test
    void thepostgresDriverCarriesTheScramFix() throws Exception {
        String version = (String) Class.forName("org.postgresql.util.DriverInfo")
                .getField("DRIVER_VERSION").get(null);

        assertThat(atLeast(version, PGJDBC_FIX))
                .as("pgjdbc %s is below the CVE-2026-54291 fix %s", version, PGJDBC_FIX)
                .isTrue();
    }

    /**
     * Every Netty artifact, not one of them. They arrive from two different
     * clients and a partial override is the shape this actually fails in: one
     * module left behind is still the vulnerable decoder on the classpath.
     */
    @Test
    void everynettyArtifactCarriesTheDecoderFix() throws Exception {
        Map<?, ?> artifacts = (Map<?, ?>) Class.forName("io.netty.util.Version")
                .getMethod("identify").invoke(null);

        assertThat(artifacts).isNotEmpty();
        assertThat(artifacts.entrySet()).allSatisfy(artifact -> {
            // "netty-common-4.1.136.Final.fca0764 (repository: dirty)" — the
            // version is what sits between the artifact id and the ".Final".
            String described = String.valueOf(artifact.getValue());
            assertThat(atLeast(described, NETTY_FIX))
                    .as("%s is below the CVE-2026-59901 fix %s", described, NETTY_FIX)
                    .isTrue();
        });
    }

    @Test
    void theembeddedTomcatCarriesTheThreeFixes() throws Exception {
        String version = (String) Class.forName("org.apache.catalina.util.ServerInfo")
                .getMethod("getServerNumber").invoke(null);

        assertThat(atLeast(version, TOMCAT_FIX))
                .as("Tomcat %s is below the fix %s (CVE-2026-65182, -65905, -68525)",
                        version, TOMCAT_FIX)
                .isTrue();

        // Staying on the 10.1 line is deliberate: 11 is the Servlet 6.1 line
        // and Boot 3 is built against 6.0. A jump would be a decision, not a
        // patch, so it fails here rather than arriving unannounced.
        assertThat(version).startsWith("10.1.");
    }

    /** All three libraries are on the classpath at all, which the reads assume. */
    @Test
    void allthreeAreStillReachable() {
        for (String type : new String[] {
                "org.postgresql.util.DriverInfo",
                "io.netty.util.Version",
                "org.apache.catalina.util.ServerInfo"}) {
            assertThatCode(() -> Class.forName(type))
                    .as("%s left the classpath; the version checks above went blind", type)
                    .doesNotThrowAnyException();
        }
    }

    /**
     * Whether the first version found in {@code described} is at or above
     * {@code floor}, compared part by part as numbers.
     *
     * <p>Written rather than borrowed because the three strings have three
     * shapes — {@code 42.7.12}, {@code netty-common-4.1.136.Final.fca0764} and
     * {@code 10.1.59.0} — and a string comparison would read 10.1.6 as newer
     * than 10.1.59.
     */
    private static boolean atLeast(String described, String floor) {
        int[] found = numbersIn(described);
        int[] required = numbersIn(floor);
        for (int part = 0; part < required.length; part++) {
            int mine = part < found.length ? found[part] : 0;
            if (mine != required[part]) {
                return mine > required[part];
            }
        }
        return true;
    }

    /**
     * The dotted number run inside a description, ignoring an artifact id in
     * front of it and anything after the numbers stop.
     */
    private static int[] numbersIn(String described) {
        var matcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)+)")
                .matcher(described);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No version in: " + described);
        }
        return java.util.Arrays.stream(matcher.group(1).split("\\."))
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}
