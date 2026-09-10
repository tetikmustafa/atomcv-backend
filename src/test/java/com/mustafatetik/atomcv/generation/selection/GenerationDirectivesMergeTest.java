package com.mustafatetik.atomcv.generation.selection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * An edit is the sum of the edits before it (Bolum 24.4).
 *
 * <p>The merge is the part of Faz G that is easy to get subtly wrong: a person
 * pressing the same switch twice expects to be back where they started, and a
 * naive union answers the constructor's refusal instead — one atom in both
 * lists.
 */
class GenerationDirectivesMergeTest {

    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();

    @Test
    void aLaterIncludeUndoesAnEarlierExclude() {
        var before = new GenerationDirectives(List.of(), List.of(FIRST));

        var after = before.and(new GenerationDirectives(List.of(FIRST), List.of()));

        assertThat(after.excludes(FIRST)).isFalse();
        assertThat(after.includes(FIRST)).isTrue();
    }

    @Test
    void aLaterExcludeUndoesAnEarlierInclude() {
        var before = new GenerationDirectives(List.of(FIRST), List.of());

        var after = before.and(new GenerationDirectives(List.of(), List.of(FIRST)));

        assertThat(after.includes(FIRST)).isFalse();
        assertThat(after.excludes(FIRST)).isTrue();
    }

    @Test
    void theEarlierEditsAreKept() {
        var before = new GenerationDirectives(List.of(FIRST), List.of());

        var after = before.and(new GenerationDirectives(List.of(), List.of(SECOND)));

        assertThat(after.includes(FIRST)).isTrue();
        assertThat(after.excludes(SECOND)).isTrue();
    }

    @Test
    void mergingNothingChangesNothing() {
        var before = new GenerationDirectives(List.of(FIRST), List.of(SECOND));

        assertThat(before.and(GenerationDirectives.none())).isEqualTo(before);
    }

    // ── the column's shape ────────────────────────────────────────────────

    @Test
    void theColumnRoundTrips() {
        var directives = new GenerationDirectives(List.of(FIRST), List.of(SECOND));

        assertThat(GenerationDirectives.fromMap(directives.asMap())).isEqualTo(directives);
    }

    @Test
    void aRowThatWasNeverEditedReadsAsNoDirectives() {
        assertThat(GenerationDirectives.fromMap(null)).isEqualTo(GenerationDirectives.none());
        assertThat(GenerationDirectives.fromMap(Map.of()))
                .isEqualTo(GenerationDirectives.none());
    }

    /** A key the writer spelled and the reader did not is a silent no-op. */
    @Test
    void theKeysAreTheOnesTheColumnHolds() {
        var stored = new GenerationDirectives(List.of(FIRST), List.of(SECOND)).asMap();

        assertThat(stored).containsOnlyKeys("includeAtoms", "excludeAtoms");
        assertThat(stored.get("includeAtoms")).isEqualTo(List.of(FIRST.toString()));
    }
}
