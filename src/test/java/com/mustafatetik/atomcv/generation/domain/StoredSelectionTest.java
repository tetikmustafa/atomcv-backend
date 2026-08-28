package com.mustafatetik.atomcv.generation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The snapshot, read back (Bolum 14.5).
 *
 * <p>{@code selection_state} is a JSONB column with rows in it, and the shape
 * that writes it moves while those rows do not. EK D.6.3 promises the PDF can
 * always be made again, which means a row written before a field existed has
 * to keep producing the document it produced then — so the reading side is
 * tested against JSON that is missing things, not against what the writing
 * side happens to emit today.
 */
class StoredSelectionTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void aSnapshotWrittenBeforeAtomlessEntriesExistedStillReads() throws Exception {
        String stored = """
                {
                  "language": "en",
                  "customization": null,
                  "budget": {"totalPt": 648.0, "fixedPt": 142.0,
                             "freePt": 506.0, "usedPt": 498.3},
                  "selected": [],
                  "rejected": []
                }
                """;

        StoredSelection snapshot = JSON.readValue(stored, StoredSelection.class);

        // Empty rather than null: every reader of this list would otherwise
        // have to defend itself, and one that forgot would fail a download of
        // a generation from before the change.
        assertThat(snapshot.headerOnlyEntries()).isEmpty();
        assertThat(snapshot.toSelectionState().headerOnlyEntries()).isEmpty();
        assertThat(snapshot.budget().usedPt()).isEqualTo(498.3);
    }

    @Test
    void anAtomlessEntryOnThePageSurvivesTheRoundTrip() throws Exception {
        UUID entryId = UUID.randomUUID();
        var state = new SelectionState(List.of(), List.of(),
                new SelectionState.BudgetBreakdown(648.0, 68.4, 579.6, 0.0),
                List.of(entryId));

        var reread = JSON.readValue(
                JSON.writeValueAsString(
                        StoredSelection.of(state, "en", TemplateCustomization.CLASSIC)),
                StoredSelection.class);

        assertThat(reread.headerOnlyEntries()).containsExactly(entryId);
        assertThat(reread.toSelectionState().isEmpty())
                .as("a page with a degree line on it is not an empty selection")
                .isFalse();
    }
}
