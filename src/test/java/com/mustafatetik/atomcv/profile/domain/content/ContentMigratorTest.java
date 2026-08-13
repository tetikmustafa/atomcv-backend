package com.mustafatetik.atomcv.profile.domain.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentMigratorTest {

    private final ContentMigrator migrator = new ContentMigrator();
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String source) {
        try {
            return mapper.readTree(source);
        } catch (Exception e) {
            throw new IllegalStateException("Malformed test fixture", e);
        }
    }

    @Test
    void readsTheStructureFromBolum14() {
        var content = migrator.read(json("""
                {
                  "v": 1,
                  "runs": [
                    { "t": "Built ", "m": [] },
                    { "t": "ETL", "m": ["technology"] },
                    { "t": " pipelines processing ", "m": [] },
                    { "t": "300K+ rows", "m": ["metric"] }
                  ]
                }
                """));

        assertThat(content.plainText()).isEqualTo("Built ETL pipelines processing 300K+ rows");
        assertThat(content.runs()).hasSize(4);
        assertThat(content.runs().get(1).hasMark(Mark.TECHNOLOGY)).isTrue();
        assertThat(content.runs().get(3).marks()).containsExactly(Mark.METRIC);
    }

    @Test
    void writeStampsTheCurrentVersion() {
        var stored = migrator.write(RichContent.plain("Built ETL pipelines"));

        assertThat(stored.get("v").asInt()).isEqualTo(ContentMigrator.CURRENT_VERSION);
        assertThat(stored.get("runs")).hasSize(1);
        assertThat(stored.get("runs").get(0).get("t").asText()).isEqualTo("Built ETL pipelines");
        assertThat(stored.get("runs").get(0).get("m")).isEmpty();
        assertThat(stored.get("runs").get(0).has("href")).isFalse();
    }

    @Test
    void roundTripPreservesMarksAndHrefs() {
        var original = RichContent.of(
                Run.of("Written up at "),
                Run.link("mustafatetik.com", "https://mustafatetik.com"),
                Run.of(" using "),
                Run.of("Go", Mark.TECHNOLOGY, Mark.EMPHASIS));

        var restored = migrator.read(migrator.write(original));

        assertThat(restored).isEqualTo(original);
        assertThat(restored.contentHash()).isEqualTo(original.contentHash());
    }

    @Test
    void aMissingVersionStampMeansVersionOne() {
        var content = migrator.read(json("""
                { "runs": [ { "t": "no version stamp", "m": [] } ] }
                """));

        assertThat(content.plainText()).isEqualTo("no version stamp");
    }

    @Test
    void aMissingMarkListMeansNoMarks() {
        var content = migrator.read(json("""
                { "v": 1, "runs": [ { "t": "bare run" } ] }
                """));

        assertThat(content.runs()).containsExactly(Run.of("bare run"));
    }

    // ─── forward compatibility (Bolum 16.2) ───

    @Test
    void anUnknownMarkSurvivesTheRoundTrip() {
        var content = migrator.read(json("""
                { "v": 1, "runs": [ { "t": "Go", "m": ["technology", "sarcasm"] } ] }
                """));

        var mark = content.runs().get(0).marks();
        assertThat(mark).containsExactly(Mark.TECHNOLOGY, new Mark("sarcasm"));
        assertThat(mark.get(1).isKnown()).isFalse();
        assertThat(migrator.write(content).get("runs").get(0).get("m").get(1).asText())
                .isEqualTo("sarcasm");
    }

    @Test
    void contentFromANewerBuildIsRefusedRatherThanMisread() {
        assertThatThrownBy(() -> migrator.read(json("""
                { "v": 2, "runs": [ { "t": "written by a newer deploy", "m": [] } ] }
                """)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version 2");
    }

    // ─── malformed rows fail loudly, and without leaking the text ───

    @Test
    void aMissingRunsArrayIsRejected() {
        assertThatThrownBy(() -> migrator.read(json("{ \"v\": 1 }")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> migrator.read(json("{ \"v\": 1, \"runs\": \"text\" }")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRunWithoutTextIsRejected() {
        assertThatThrownBy(() -> migrator.read(json("{ \"v\": 1, \"runs\": [ { \"m\": [] } ] }")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNonTextualMarkIsRejected() {
        assertThatThrownBy(() -> migrator.read(json("""
                { "v": 1, "runs": [ { "t": "Go", "m": [7] } ] }
                """)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFailureMessageNeverCarriesTheRunText() {
        assertThatThrownBy(() -> migrator.read(json("""
                { "v": 1, "runs": [ { "t": "kept", "m": [] }, { "m": ["metric"] } ] }
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Run 1 has no text");
    }

    @Test
    void emptyContentRoundTrips() {
        assertThat(migrator.read(migrator.write(RichContent.EMPTY))).isEqualTo(RichContent.EMPTY);
        assertThat(migrator.read(json("{ \"v\": 1, \"runs\": [] }")).runs()).isEqualTo(List.of());
    }
}
