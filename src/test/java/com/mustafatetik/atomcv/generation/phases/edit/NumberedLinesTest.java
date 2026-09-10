package com.mustafatetik.atomcv.generation.phases.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The reason a model cannot invent an atom (Bolum 24.2).
 *
 * <p>The ids never leave this object. What the model is shown is numbers, what
 * it answers with is numbers, and a number out of range is obviously out of
 * range — which a well-formed UUID naming nothing is not.
 */
class NumberedLinesTest {

    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    private static final UUID HELD = UUID.randomUUID();

    @Test
    void theNumberingRunsThroughBothListsWithoutRestarting() {
        var lines = NumberedLines.of(
                List.of(line(FIRST, "Built the Android client"),
                        line(SECOND, "Cut checkout latency by 40%")),
                List.of(line(HELD, "Ran the Kubernetes migration")));

        assertThat(lines.idAt(1)).isEqualTo(FIRST);
        assertThat(lines.idAt(2)).isEqualTo(SECOND);
        assertThat(lines.idAt(3)).isEqualTo(HELD);
        assertThat(lines.size()).isEqualTo(3);
    }

    @Test
    void anumberThatWasNeverOfferedIsNotAline() {
        var lines = NumberedLines.of(List.of(line(FIRST, "one")), List.of());

        assertThat(lines.has(1)).isTrue();
        assertThat(lines.has(2)).isFalse();
        assertThat(lines.has(0)).isFalse();
        assertThat(lines.has(-1)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> lines.idAt(2));
    }

    /**
     * The guard the whole design rests on. If an id ever reached the prompt,
     * the model could echo one back and the numbering would be decoration.
     */
    @Test
    void noAtomIdReachesThePrompt() {
        var lines = NumberedLines.of(
                List.of(line(FIRST, "Built the Android client")),
                List.of(line(HELD, "Ran the Kubernetes migration")));

        String data = lines.asPromptData("take out the android one");

        assertThat(data)
                .doesNotContain(FIRST.toString())
                .doesNotContain(HELD.toString());
    }

    @Test
    void thePromptCarriesTheSentenceAndBothLists() {
        var lines = NumberedLines.of(
                List.of(line(FIRST, "Built the Android client")),
                List.of(line(HELD, "Ran the Kubernetes migration")));

        String data = lines.asPromptData("take out the android one");

        assertThat(data)
                .contains("take out the android one")
                .contains("1. Built the Android client")
                .contains("2. Ran the Kubernetes migration");
    }

    /** A full page and nothing held back is ordinary, and so is the reverse. */
    @Test
    void anemptyListSaysSoRatherThanVanishing() {
        var lines = NumberedLines.of(List.of(line(FIRST, "one")), List.of());

        assertThat(lines.asPromptData("drop it")).contains("heldBack:").contains("(none)");
    }

    /** Absolute rule 4: every line here is the user's own writing. */
    @Test
    void thetoStringCountsAndDoesNotQuote() {
        var lines = NumberedLines.of(
                List.of(line(FIRST, "Built the Android client")), List.of());

        assertThat(lines.toString()).contains("page=1").doesNotContain("Android");
    }

    private static NumberedLines.Line line(UUID atomId, String text) {
        return new NumberedLines.Line(atomId, text);
    }
}
