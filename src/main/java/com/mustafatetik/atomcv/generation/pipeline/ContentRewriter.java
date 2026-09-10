package com.mustafatetik.atomcv.generation.pipeline;

import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Faz D, as the pipeline sees it (Bolum 21).
 *
 * <p>A parameter rather than a dependency, for two reasons. General mode has
 * no posting, so it has nothing to adapt a sentence towards and passes
 * {@link #none()}; and everything Faz D needs beyond the selection — the
 * posting's skills, the language, the tone, the experiment bucket — is held by
 * the service that read them, not by a pipeline whose job is the page limit.
 *
 * <p>The second argument is what earlier attempts already paid for. The
 * pipeline shrinks the budget and selects again when a document runs long, and
 * an implementation is expected to answer for the atoms it has already
 * rewritten without asking a model a second time.
 */
@FunctionalInterface
public interface ContentRewriter {

    RewrittenContent rewrite(SelectionState selection, RewrittenContent carried);

    /** No posting to write towards: every sentence is printed as written. */
    static ContentRewriter none() {
        return (selection, carried) -> carried;
    }

    /**
     * A re-run that keeps what Faz D already wrote and asks it nothing new
     * (Bolum 24.1).
     *
     * <p>This is what makes a hand edit free. Switching one bullet off changes
     * neither the posting nor what the model would say about the bullets that
     * stayed, so the sentences come back out of
     * {@code generations.rewritten_content} and no request goes out. An atom
     * the edit newly puts on the page has no rewrite here and is printed the
     * way the person wrote it — which is not a gap but Bolum 21.6's own rule:
     * absent means original, and Faz E cannot get that wrong.
     *
     * <p>Pruned to what is on the page. The map is stored again with the
     * generation this produces, and carrying a rewrite for an atom that was
     * removed three edits ago would grow the column forever with sentences no
     * document contains.
     *
     * @param inherited what the generation being replaced had written
     */
    static ContentRewriter carrying(RewrittenContent inherited) {
        RewrittenContent held = inherited == null ? RewrittenContent.none() : inherited;
        return (selection, carried) -> {
            var kept = new LinkedHashMap<UUID, RichContent>();
            for (SelectionState.SelectedAtom atom : selection.selected()) {
                RichContent line = held.byAtom().get(atom.atomId());
                if (line != null) {
                    kept.put(atom.atomId(), line);
                }
            }
            // What this run produced wins: on the second turn of the compile
            // loop `carried` is the previous attempt's answer, and it is newer
            // than the row we inherited from.
            return new RewrittenContent(kept).and(carried.byAtom());
        };
    }
}
