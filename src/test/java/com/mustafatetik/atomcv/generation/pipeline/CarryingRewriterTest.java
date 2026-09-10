package com.mustafatetik.atomcv.generation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Faz D on a re-run: it answers out of the parent row and asks nothing
 * (Bolum 24.1).
 *
 * <p>This is what makes a hand edit free, so the thing worth checking is not
 * that it produces sentences but that it produces them <em>without a model</em>
 * and only for atoms that are on the page.
 */
class CarryingRewriterTest {

    private static final UUID KEPT = UUID.randomUUID();
    private static final UUID DROPPED = UUID.randomUUID();
    private static final UUID NEWLY_ON = UUID.randomUUID();

    @Test
    void anAtomThatStayedKeepsItsWording() {
        var inherited = new RewrittenContent(Map.of(KEPT, RichContent.plain("Faz D wrote this")));

        var answer = ContentRewriter.carrying(inherited)
                .rewrite(pageOf(KEPT), RewrittenContent.none());

        assertThat(answer.orOriginal(KEPT, RichContent.plain("original")))
                .isEqualTo(RichContent.plain("Faz D wrote this"));
    }

    /**
     * Bolum 21.6's own rule, and the reason a free edit is honest rather than
     * half-finished: absent means original, and Faz E cannot get that wrong.
     */
    @Test
    void anAtomTheEditPutOnThePageIsPrintedAsWritten() {
        var inherited = new RewrittenContent(Map.of(KEPT, RichContent.plain("Faz D wrote this")));

        var answer = ContentRewriter.carrying(inherited)
                .rewrite(pageOf(KEPT, NEWLY_ON), RewrittenContent.none());

        assertThat(answer.covers(NEWLY_ON)).isFalse();
    }

    /**
     * The map is stored again with the generation this produces. Carrying a
     * sentence for an atom that was removed three edits ago would grow the
     * column forever with text no document contains.
     */
    @Test
    void anAtomTheEditRemovedIsNotCarried() {
        var inherited = new RewrittenContent(Map.of(
                KEPT, RichContent.plain("still here"),
                DROPPED, RichContent.plain("taken off the page")));

        var answer = ContentRewriter.carrying(inherited)
                .rewrite(pageOf(KEPT), RewrittenContent.none());

        assertThat(answer.byAtom()).containsOnlyKeys(KEPT);
    }

    /**
     * The compile loop goes round again with a smaller budget, and what it
     * carries is newer than the row we inherited from.
     */
    @Test
    void thisRunOutranksTheInheritedRow() {
        var inherited = new RewrittenContent(Map.of(KEPT, RichContent.plain("from the parent")));
        var carried = new RewrittenContent(Map.of(KEPT, RichContent.plain("from this run")));

        var answer = ContentRewriter.carrying(inherited).rewrite(pageOf(KEPT), carried);

        assertThat(answer.orOriginal(KEPT, RichContent.plain("original")))
                .isEqualTo(RichContent.plain("from this run"));
    }

    @Test
    void aParentThatNeverRanFazDcarriesNothing() {
        var answer = ContentRewriter.carrying(null)
                .rewrite(pageOf(KEPT), RewrittenContent.none());

        assertThat(answer.isEmpty()).isTrue();
    }

    private static SelectionState pageOf(UUID... atomIds) {
        var selected = List.of(atomIds).stream()
                .map(id -> new SelectionState.SelectedAtom(id, UUID.randomUUID(), 0.5, 25.0, false))
                .toList();
        return new SelectionState(selected, List.of(),
                new SelectionState.BudgetBreakdown(648.0, 142.0, 506.0, 25.0));
    }
}
