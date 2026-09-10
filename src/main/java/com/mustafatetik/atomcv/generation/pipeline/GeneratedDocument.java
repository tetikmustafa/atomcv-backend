package com.mustafatetik.atomcv.generation.pipeline;

import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
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
 * @param rewritten  what Faz D replaced, by atom. Carried out whole rather
 *                   than counted, because {@code generations.rewritten_content}
 *                   stores it and Faz G's re-run reads it back: an edit that
 *                   moved one bullet must not pay for every other bullet's
 *                   sentences a second time (V11, Bolum 24)
 */
public record GeneratedDocument(
        byte[] pdf,
        int pageCount,
        SelectionState selection,
        RenderRequest rendered,
        int attempts,
        double budgetFactor,
        RewrittenContent rewritten) {

    /**
     * How many atoms Faz D actually replaced.
     *
     * <p>Zero is a real answer and not a missing one — general mode has no
     * posting to write towards — but zero <em>with</em> a posting is the phase
     * having quietly done nothing, which is how a scoring run without
     * embeddings looks from the outside.
     */
    public int rewrittenAtoms() {
        return rewritten.byAtom().size();
    }

    /** Shape only: the document is the user's own content. */
    @Override
    public String toString() {
        return "GeneratedDocument[pages=" + pageCount + ", bytes=" + pdf.length
                + ", atoms=" + selection.selected().size() + ", attempts=" + attempts + "]";
    }
}
