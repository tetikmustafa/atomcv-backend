package com.mustafatetik.atomcv.ingestion.structuring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The recorded answer that was thrown away, replayed against the audit.
 *
 * <p>This is the four-page CV of Bulgu 1: 84 atoms, six sections, and a single
 * 607-character About paragraph that cost all of them. It is checked against
 * the real file rather than a hand-written string so that the fixture and the
 * ceiling cannot drift apart silently.
 */
class RecordedExtractionAuditTest {

    @Test
    void theFourPageCvThatWasRefusedNowPasses() throws Exception {
        var profile = new ObjectMapper().readValue(
                Path.of("src/test/resources/fixtures/llm/profile_extraction",
                        "v1-d506ef8f97bb.json").toFile(),
                ExtractedProfile.class);

        assertThat(profile.atoms()).as("the answer really did carry a whole CV").hasSize(84);
        assertThat(StructuringAudit.abnormalField(profile)).isEmpty();
    }
}
