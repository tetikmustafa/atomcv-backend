package com.mustafatetik.atomcv.generation.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.List;

/**
 * {@code generations.selection_state} as Bolum 14.5 defines it.
 *
 * <p>{@link SelectionState} is what the pipeline passes around; this is what
 * the column holds, and it carries two things the pipeline's version does not
 * need in memory and cannot do without on disk: the language and the
 * customization. Without them the snapshot describes <em>which</em> atoms were
 * chosen but not how to draw them, and EK D.6.3's promise — that the PDF can
 * always be made again — would not hold.
 *
 * <p><strong>Sapma from Bolum 14.5:</strong> the field is the customization
 * itself and not a {@code customizationId}. There is no
 * {@code template_customizations} row to point at yet — Stage 2 renders with a
 * constant — and an id that resolves to nothing would make the snapshot
 * unusable for exactly the thing it exists for. The font, margin and spacing
 * are what a re-render needs; when the table has rows, an id can join to them
 * and this stays as the record of what actually ran.
 *
 * <p>That promise is what this column is for. In Stage 2 it is not a fallback
 * for an expired artifact but the only path: nothing stores the bytes, and
 * {@code pdf_key} stays null until R2 arrives in Stage 3. A download re-renders
 * from here, which costs a compilation and no LLM call at all.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoredSelection(
        String language,
        TemplateCustomization customization,
        SelectionState.BudgetBreakdown budget,
        List<SelectionState.SelectedAtom> selected,
        List<SelectionState.RejectedAtom> rejected) {

    public StoredSelection {
        language = language == null ? "" : language;
        selected = selected == null ? List.of() : List.copyOf(selected);
        rejected = rejected == null ? List.of() : List.copyOf(rejected);
    }

    public static StoredSelection of(
            SelectionState state, String language, TemplateCustomization customization) {

        return new StoredSelection(language, customization, state.budget(),
                state.selected(), state.rejected());
    }

    /** Back to what the pipeline works on, for a regenerated download. */
    public SelectionState toSelectionState() {
        return new SelectionState(selected, rejected, budget);
    }
}
