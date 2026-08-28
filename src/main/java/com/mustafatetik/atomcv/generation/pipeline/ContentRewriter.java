package com.mustafatetik.atomcv.generation.pipeline;

import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.selection.SelectionState;

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
}
