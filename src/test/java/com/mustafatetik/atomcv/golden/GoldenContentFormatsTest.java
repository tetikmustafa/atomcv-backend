package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.profile.domain.content.ContentMigrator;
import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.latex.LatexInlineRenderer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

/**
 * A stored document of each JSONB version, read back.
 *
 * <p><strong>What the directory is for.</strong> {@code ContentMigrator} is a
 * lazy upgrade path: a row written under an older stamp is walked forward when
 * it is read. That path has no test worth the name unless there is a document
 * somebody actually stored to walk — and on the day a v2 exists, a v1 document
 * written by whoever wrote the migration proves only that the migration reads
 * what its author expected. The fixture is written now, while v1 is current and
 * nobody has a v2 in mind.
 *
 * <p>Today there is one version, so this reads as an unremarkable round trip.
 * That is the state it is meant to be in.
 */
class GoldenContentFormatsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ContentMigrator migrator = new ContentMigrator();

    @Test
    void thestoredVersionOneDocumentReadsBackAsItWasWritten() {
        RichContent content = migrator.read(stored("v1"));

        assertThat(content.runs()).hasSize(13);
        assertThat(content.plainText())
                .startsWith("Engineered ETL pipelines at Brisa")
                .endsWith("freeing a whole shift.");
        assertThat(content.runs().get(1).marks()).containsExactly(Mark.TECHNOLOGY);
        assertThat(content.runs().get(7).href()).isEqualTo("https://example.com/adr-14");
    }

    /**
     * <strong>Bolum 16.2's forward compatibility, and the only way to hold
     * it.</strong> A mark this version has never heard of is read, kept and
     * rendered as plain text. Kept is the half that matters: a reader that
     * dropped it would silently delete a newer version's markings the moment
     * somebody edited that sentence (EK D.9, rule 2).
     */
    @Test
    void amarkThisVersionDoesNotKnowSurvivesTheRoundTrip() {
        RichContent content = migrator.read(stored("v1"));

        assertThat(content.runs().get(11).marks())
                .as("an unknown mark is kept, not dropped")
                .containsExactly(new Mark("experimental"));

        assertThat(migrator.write(content).toString())
                .as("and it is still there when the row is written back")
                .contains("experimental");
    }

    /** And the renderer prints it rather than failing on it (Bolum 22.3). */
    @Test
    void theRendererPrintsAnunknownMarkAsPlainText() {
        String latex = LatexInlineRenderer.render(migrator.read(stored("v1")));

        assertThat(latex)
                .contains("\\textbf{ETL}")
                .contains("freeing a whole shift")
                .doesNotContain("experimental");
    }

    private static JsonNode stored(String version) {
        String resource = "golden/content-formats/" + version + ".json";
        try (InputStream in = GoldenContentFormatsTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No golden document at " + resource);
            }
            // The file carries its own explanation under a key the format does
            // not have; what the reader is handed is the document itself.
            return JSON.readTree(in).get("document");
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
