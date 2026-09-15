package com.mustafatetik.atomcv.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The catalogue in git, against the catalogue {@link ErrorCode} describes.
 *
 * <p><strong>What this replaces.</strong> The table was written by hand in the
 * architecture document and this test read it back and compared. That worked
 * and it caught real drift — five codes were missing the first time it ran —
 * but it made the document a second authority that had to be edited in step,
 * and it tied the suite to a path under {@code docs/}. Generating the table
 * removes both: there is one authority, it is compiled, and the file it
 * produces lives beside {@code openapi.json} where the frontend can fetch it
 * wherever the prose ends up.
 *
 * <p><strong>Why a committed file rather than one produced at build time.</strong>
 * The same reason {@code openapi.json} is committed: the other repository reads
 * it, and a file under {@code build/} is a file that is not there. Committing
 * it also makes every change to the contract visible in a diff, which is what
 * a reviewer needs and a generated-on-demand file never gives.
 *
 * <p><strong>Recording.</strong> {@code make catalogue} — or
 * {@code -Dcatalogue.record=true} — rewrites the file instead of asserting on
 * it, the same idiom as {@code -Dopenapi.record=true}.
 */
class ErrorCatalogueDocumentTest {

    /** From the repository root, which is where Gradle runs a test from. */
    private static final Path COMMITTED = Path.of("error-catalogue.md");

    @Test
    void thecatalogueInGitIsTheCatalogueTheCodeDescribes() throws IOException {
        String rendered = ErrorCatalogueDocument.render();

        if (Boolean.getBoolean("catalogue.record")) {
            // LF written by hand: the editor and Python's text mode write CRLF
            // on this machine and git normalises on commit, so a file written
            // with platform endings differs locally and matches on the runner
            // -- a failure that reads as a flake and is not one (CLAUDE.md).
            Files.writeString(COMMITTED, rendered, StandardCharsets.UTF_8);
            return;
        }

        assertThat(COMMITTED)
                .as("error-catalogue.md is missing; run `make catalogue`")
                .exists();
        assertThat(Files.readString(COMMITTED, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .as("the catalogue has moved on from the committed file -- run "
                        + "`make catalogue` and commit it, so the frontend has "
                        + "something true to write its messages from")
                .isEqualTo(rendered);
    }

    /**
     * The document was actually produced, rather than being an empty string
     * two comparisons agreed about.
     */
    @Test
    void everycodeReachesTheTable() {
        String rendered = ErrorCatalogueDocument.render();

        assertThat(ErrorCode.values()).hasSizeGreaterThan(30);
        assertThat(ErrorCode.values()).allSatisfy(code ->
                assertThat(rendered).contains("| `" + code.name() + "` | " + code.httpStatus()));
    }

    /**
     * <strong>Two types may not print the same word.</strong> The spelling is
     * what an ICU placeholder is written against, so a duplicate would let a
     * message format a list as a string and pass every test here.
     */
    @Test
    void everytypeHasItsOwnSpelling() {
        assertThat(Arrays.stream(ParamType.values()).map(ParamType::wireName).toList())
                .doesNotHaveDuplicates()
                .allSatisfy(name -> assertThat(name).isNotBlank());
    }

    /**
     * A code with no parameters prints an em dash, not an empty cell and not a
     * parameter named after one. The old hand-written table had this
     * convention and the readers downstream still expect it.
     */
    @Test
    void acodeWithNoParametersPrintsADash() {
        assertThat(ErrorCode.EMBEDDING_UNAVAILABLE.params()).isEmpty();
        assertThat(ErrorCatalogueDocument.render())
                .contains("| `EMBEDDING_UNAVAILABLE` | 503 | " + ErrorCatalogueDocument.NONE + " |");
    }
}
