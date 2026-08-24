package com.mustafatetik.atomcv.profile.domain.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Rich content survives a JSONB column (Bolum 12, Bolum 14.1).
 *
 * <p>Written after a 500 that only appeared with a real database in the loop:
 * {@code Mark} exposed a derived {@code isKnown()} that Jackson wrote out and
 * then refused to read back, so a column round trip failed on its own output.
 * Two columns hold this shape — {@code atom_variants.content} and
 * {@code generations.content_snapshot} — and the second one is what a download
 * re-renders, so the failure surfaced as a CV that could not be fetched again.
 *
 * <p>Plain Jackson rather than a database, because what broke was the
 * serialisation and not the column. A test needing Postgres to say this would
 * have been slower and no more truthful.
 */
class RichContentJsonTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void amarkedRunSurvivesTheRoundTrip() throws Exception {
        var original = RichContent.of(
                Run.of("Built "),
                Run.of("ETL", Mark.TECHNOLOGY),
                Run.of(" pipelines processing "),
                Run.of("300K+ rows", Mark.METRIC));

        var written = json.writeValueAsString(original);

        assertThat(json.readValue(written, RichContent.class)).isEqualTo(original);
    }

    /**
     * The field that caused it. Derived from a set this build happens to hold,
     * so writing it puts one release's opinion into a column that outlives the
     * release — and Jackson could not read it back at all.
     */
    @Test
    void thederivedKnownFlagIsNeverWritten() throws Exception {
        assertThat(json.writeValueAsString(Mark.TECHNOLOGY)).doesNotContain("known");
        assertThat(json.writeValueAsString(RichContent.of(Run.of("x", Mark.METRIC))))
                .doesNotContain("known");
    }

    /**
     * Bolum 12: stored content may carry a mark written by a newer build.
     * Parsing must not fail and the round trip must not drop it.
     */
    @Test
    void amarkThisBuildDoesNotKnowSurvivesAnyway() throws Exception {
        var future = RichContent.of(Run.of("text", new Mark("something-newer")));

        var reread = json.readValue(json.writeValueAsString(future), RichContent.class);

        assertThat(reread).isEqualTo(future);
        assertThat(reread.runs().get(0).marks()).allMatch(mark -> !mark.isKnown());
    }

    /**
     * Rows written by the build that had the bug carry {@code empty} and
     * {@code known}. Refusing them after the fix would make every stored
     * profile unreadable — a correctness fix that destroys data is not a fix.
     */
    @Test
    void rowsWrittenByTheBrokenBuildAreStillReadable() throws Exception {
        String asStoredBefore = """
                {"runs":[{"text":"Built ","marks":[],"href":null},
                         {"text":"ETL","marks":[{"value":"technology","known":true}],
                          "href":null}],
                 "empty":false,"plainText":"Built ETL","contentHash":"stale"}
                """;

        var reread = json.readValue(asStoredBefore, RichContent.class);

        assertThat(reread.plainText()).isEqualTo("Built ETL");
        assertThat(reread.runs().get(1).marks()).containsExactly(Mark.TECHNOLOGY);
    }

    @Test
    void plaintextSurvivesToo() throws Exception {
        var original = RichContent.plain("Ran the on-call rota");

        assertThat(json.readValue(json.writeValueAsString(original), RichContent.class))
                .isEqualTo(original);
    }
}
