package com.mustafatetik.atomcv.generation.pipeline;

import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;

/**
 * A finished CV: the document, and the record of how it came to look that way.
 *
 * <p>The selection state travels with the PDF because it, not the PDF, is what
 * an edit later applies to (design principle 6) and what makes every choice
 * explainable (design principle 7).
 *
 * @param rendered   exactly what went to the renderer. Carried out because
 *                   {@code generations.content_snapshot} stores it: the
 *                   selection names atoms by id and the text under those ids
 *                   keeps changing, so a download that re-read the profile
 *                   would hand back a different document from the one that was
 *                   sent to an employer.
 * @param attempts   how many compilations it took; more than one means the
 *                   measurement was optimistic and the budget had to shrink
 * @param budgetFactor the share of the page the last attempt allowed itself
 */
public record GeneratedDocument(
        byte[] pdf,
        int pageCount,
        SelectionState selection,
        RenderRequest rendered,
        int attempts,
        double budgetFactor) {

    /** Shape only: the document is the user's own content. */
    @Override
    public String toString() {
        return "GeneratedDocument[pages=" + pageCount + ", bytes=" + pdf.length
                + ", atoms=" + selection.selected().size() + ", attempts=" + attempts + "]";
    }
}
