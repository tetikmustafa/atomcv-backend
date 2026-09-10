package com.mustafatetik.atomcv.generation.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The record that became a column (V11).
 *
 * <p>None of this was checked while the map died with the run that made it.
 * The order below is about what walks the map in memory — a trace, a log line,
 * an assertion — and not about the column: {@code jsonb} sorts an object's
 * keys itself, which {@code GenerationRecordIT} says out loud.
 */
class RewrittenContentTest {

    @Test
    void theMapKeepsTheOrderItWasGiven() {
        var ids = new LinkedHashMap<UUID, RichContent>();
        List<UUID> written = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        for (UUID id : written) {
            ids.put(id, RichContent.plain("a line"));
        }

        assertThat(new RewrittenContent(ids).byAtom().keySet())
                .containsExactlyElementsOf(written);
    }

    @Test
    void aLaterPassWinsATie() {
        UUID atomId = UUID.randomUUID();
        var first = new RewrittenContent(Map.of(atomId, RichContent.plain("first")));

        var merged = first.and(Map.of(atomId, RichContent.plain("second")));

        assertThat(merged.orOriginal(atomId, RichContent.plain("original")))
                .isEqualTo(RichContent.plain("second"));
    }

    @Test
    void anAtomItDoesNotCoverIsPrintedAsWritten() {
        var content = RewrittenContent.none();
        RichContent original = RichContent.plain("what the person wrote");

        assertThat(content.covers(UUID.randomUUID())).isFalse();
        assertThat(content.orOriginal(UUID.randomUUID(), original)).isEqualTo(original);
    }

    /** Absolute rule 4: a bullet is the user's own writing. */
    @Test
    void theToStringCountsAndDoesNotQuote() {
        var content = new RewrittenContent(
                Map.of(UUID.randomUUID(), RichContent.plain("Cut latency by 40%")));

        assertThat(content.toString()).contains("atoms=1").doesNotContain("latency");
    }
}
