package com.mustafatetik.atomcv.generation.rewrite;

/**
 * What one pass of Faz D produced (Bolum 21.5, Bolum 14.6).
 *
 * <p>Two halves that go to two different places and must not be folded into
 * one: {@link RewrittenContent} is what the renderer reads and is deliberately
 * blind to everything that did not change, and {@link RewriteTally} is what the
 * trace reads and is about nothing else.
 *
 * @param content what to print
 * @param tally   what it cost and what it refused
 */
public record RewriteOutcome(RewrittenContent content, RewriteTally tally) {

    /** Faz D did not run: nothing changed and nothing was spent. */
    public static RewriteOutcome of(RewrittenContent carried) {
        return new RewriteOutcome(carried, RewriteTally.none());
    }
}
