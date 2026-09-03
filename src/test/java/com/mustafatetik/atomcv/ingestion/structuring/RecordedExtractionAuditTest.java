package com.mustafatetik.atomcv.ingestion.structuring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A whole four-page CV, replayed against Bolum 43.1's third layer.
 *
 * <p>This is the extraction that was thrown away: 84 atoms, six sections, and
 * one About paragraph of 607 characters against a ceiling of 600 that had been
 * argued from what fits in a bullet. {@code StructuringAuditTest} checks the
 * boundary with a sentence written for the purpose; this checks that a real
 * document's worth of fields — titles, employers, skills, proper nouns, eighty
 * four bullets — passes it end to end, which is what a per-field rule can fail
 * in a way one sentence cannot show.
 *
 * <p><strong>The fixture is redacted, and it has to be.</strong> A recorded
 * {@code profile_extraction} answer <em>is</em> the person's CV — name, address,
 * telephone, every link — and {@code .gitignore} keeps that whole directory out
 * of a public repository for exactly that reason. Committing the real one to
 * make a test run would publish it permanently. So what is committed here has
 * the same shape and the same lengths with every string replaced: the audit
 * measures lengths, so the redaction changes nothing it looks at, and it can
 * be read by anyone.
 *
 * <p>The real answer is still checked when the machine happens to have it —
 * after {@code make record}, on the developer's own clone — because a redaction
 * that had drifted from the thing it stands for would be worth knowing about.
 */
class RecordedExtractionAuditTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Redacted, committed, and read from the classpath rather than the disk. */
    private static final String SHAPE = "/fixtures/extraction-audit-shape.json";

    /** The real recording, present only where someone has run {@code make record}. */
    private static final Path RECORDED = Path.of(
            "src/test/resources/fixtures/llm/profile_extraction/v1-d506ef8f97bb.json");

    @Test
    void awholeExtractedCvPassesTheAudit() throws Exception {
        ExtractedProfile profile = shape();

        assertThat(profile.atoms())
                .as("the answer really did carry a whole CV")
                .hasSize(84);
        assertThat(longestAtomText(profile))
                .as("and the field that refused it is still over the bullet ceiling")
                .isGreaterThan(600);

        assertThat(StructuringAudit.abnormalField(profile)).isEmpty();
    }

    /**
     * The redaction stands for the recording, so it has to still measure like
     * it. Skipped where the recording is not on disk, which is everywhere but
     * a clone that has recorded one.
     */
    @Test
    void theRedactionStillMeasuresLikeTheAnswerItStandsFor() throws Exception {
        if (!Files.exists(RECORDED)) {
            return;
        }
        ExtractedProfile recorded = JSON.readValue(RECORDED.toFile(), ExtractedProfile.class);

        assertThat(shape().atoms()).hasSameSizeAs(recorded.atoms());
        assertThat(longestAtomText(shape())).isEqualTo(longestAtomText(recorded));
        assertThat(StructuringAudit.abnormalField(recorded)).isEmpty();
    }

    private static ExtractedProfile shape() throws Exception {
        try (InputStream in = RecordedExtractionAuditTest.class.getResourceAsStream(SHAPE)) {
            assertThat(in).as("%s is on the test classpath", SHAPE).isNotNull();
            return JSON.readValue(in, ExtractedProfile.class);
        }
    }

    private static int longestAtomText(ExtractedProfile profile) {
        return profile.sections().stream()
                .flatMap(section -> section.entries().stream())
                .flatMap(entry -> entry.atoms().stream())
                .mapToInt(atom -> atom.textSource().length())
                .max()
                .orElse(0);
    }
}
