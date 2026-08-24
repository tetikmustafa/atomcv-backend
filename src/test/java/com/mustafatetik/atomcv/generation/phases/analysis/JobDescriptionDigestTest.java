package com.mustafatetik.atomcv.generation.phases.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The hash two subsystems have to agree on (Bolum 18.6, Bolum 13).
 *
 * <p>Worth its own class because the failure it guards against is silent: the
 * cache and {@code generations.jd_hash} would simply stop matching, no error
 * anywhere, and a posting analysed twice would look like a cache that is
 * merely cold.
 */
class JobDescriptionDigestTest {

    /**
     * The bug this caught. {@code "\s"} is a valid Java string escape since
     * Java 15 and means one plain space, so a pattern written with a single
     * backslash compiles fine and silently narrows to runs of spaces — line
     * endings survive, and the same posting from a PDF hashes differently from
     * the same posting from a browser.
     */
    @Test
    void everyKindOfWhitespaceCollapses() {
        assertThat(JobDescriptionDigest.normalize("a\r\n\r\nb")).isEqualTo("a b");
        assertThat(JobDescriptionDigest.normalize("a\t\tb")).isEqualTo("a b");
        assertThat(JobDescriptionDigest.normalize("a\n b")).isEqualTo("a b");
        assertThat(JobDescriptionDigest.normalize("   a   b   ")).isEqualTo("a b");
    }

    @Test
    void thesamePostingFromAPdfAndFromABrowserHashTheSame() {
        var fromBrowser = "We are seeking a senior backend engineer.\nApply within.";
        var fromPdf = "  We are seeking  a senior backend\r\n\r\nengineer.   Apply within.  ";

        assertThat(JobDescriptionDigest.of(fromPdf))
                .isEqualTo(JobDescriptionDigest.of(fromBrowser));
    }

    @Test
    void adifferentPostingHashesDifferently() {
        assertThat(JobDescriptionDigest.of("one posting"))
                .isNotEqualTo(JobDescriptionDigest.of("another posting"));
    }

    /** Lowercase hex of a SHA-256, which is 64 characters. */
    @Test
    void thehashIsAStableShape() {
        String hash = JobDescriptionDigest.of("a posting");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hash).isEqualTo(JobDescriptionDigest.of("a posting"));
    }

    /** The cache delegates here, so the two cannot drift. */
    @Test
    void thecacheNormalisesThroughTheSameCode() {
        assertThat(JobAnalysisCache.normalize("a\r\n\r\nb"))
                .isEqualTo(JobDescriptionDigest.normalize("a\r\n\r\nb"));
    }
}
