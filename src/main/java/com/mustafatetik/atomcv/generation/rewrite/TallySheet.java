package com.mustafatetik.atomcv.generation.rewrite;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link RewriteTally} while it is still being written.
 *
 * <p>Package-private and short-lived: one of these belongs to one atom's two
 * attempts, or to one pass of {@link RewritePhase}, and is sealed into a record
 * the moment that finishes. Nothing outside this package ever sees a mutable
 * count.
 *
 * <p><strong>Not thread-safe, and does not need to be.</strong> Faz D fans out
 * one sheet per task; the phase merges the sealed results on the joining
 * thread.
 */
final class TallySheet {

    private final Map<String, Integer> calls = new LinkedHashMap<>();
    private final Map<RewriteIssue, Integer> refusals = new EnumMap<>(RewriteIssue.class);
    private int unreachable;

    /** One request went out, whatever came back. */
    void called(String promptId) {
        calls.merge(promptId, 1, Integer::sum);
    }

    /** It never reached a model, so nothing was judged. */
    void unreachable() {
        unreachable++;
    }

    /** A model answered and the validator turned the answer away. */
    void refused(List<RewriteIssue> issues) {
        for (RewriteIssue issue : issues) {
            refusals.merge(issue, 1, Integer::sum);
        }
    }

    RewriteTally sealed() {
        return new RewriteTally(calls, refusals, unreachable);
    }
}
