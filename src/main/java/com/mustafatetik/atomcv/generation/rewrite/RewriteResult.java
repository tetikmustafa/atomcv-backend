package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;

/**
 * One atom's answer, and what it took to get it (Bolum 21.6, Bolum 14.6).
 *
 * <p>The content half is the whole of what Faz E needs: the rewrite when it
 * passed, and the person's own sentence when it did not, with the caller unable
 * to tell which. The tally is the half that was previously thrown away — a
 * bullet refused twice for {@link RewriteIssue#UNSUPPORTED_CLAIM} and a bullet
 * nobody could reach a model for both come back here as the original, and
 * nothing downstream could distinguish them.
 *
 * @param content what to print for this atom
 * @param tally   the calls it made and the reasons it refused what came back
 */
public record RewriteResult(RichContent content, RewriteTally tally) {

    /** No call was made — the caller kept what the person wrote. */
    public static RewriteResult kept(RichContent original) {
        return new RewriteResult(original, RewriteTally.none());
    }
}
