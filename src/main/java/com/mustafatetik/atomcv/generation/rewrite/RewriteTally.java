package com.mustafatetik.atomcv.generation.rewrite;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What Faz D did, beyond what it changed (Bolum 14.6).
 *
 * <p>{@link RewrittenContent} carries the accepted rewrites and, by design,
 * nothing else — an atom the model refused twice and one that was never a
 * candidate are the same thing to Faz E. That is the right rule for the
 * renderer and the wrong one for the trace: a generation that printed the
 * profile verbatim because every rewrite was refused for
 * {@link RewriteIssue#UNSUPPORTED_CLAIM} and one that printed it verbatim
 * because the provider was down look identical from the outside, and the two
 * call for opposite reactions. This is the second half, kept beside the first
 * instead of inside it.
 *
 * <p><strong>Counts only, never a sentence</strong> — absolute rule 4. A
 * {@link RewriteIssue} is a kind with no text in it for the same reason.
 *
 * @param callsByPrompt how many model calls each prompt actually made. This is
 *                      what separates "the prompt ran and everything it
 *                      produced was refused" from "the prompt never ran", which
 *                      is also what {@code engine_version.promptVersions} needs:
 *                      naming {@code bullet_rewrite} for a generation whose only
 *                      call was an About synthesis sends whoever reads the record
 *                      back to the wrong prompt.
 * @param refusals      how many attempts each issue turned away. One attempt can
 *                      raise several — a rewrite that is both too long and short
 *                      a number counts under both — so these sum to at least the
 *                      number of refused attempts, not exactly it.
 * @param unreachable   attempts that never reached a model. Not a rewrite
 *                      failure: the sentence was never judged, and the reaction
 *                      is to look at the provider chain rather than the prompt.
 */
public record RewriteTally(
        Map<String, Integer> callsByPrompt,
        Map<RewriteIssue, Integer> refusals,
        int unreachable) {

    private static final RewriteTally NONE =
            new RewriteTally(Map.of(), Map.of(), 0);

    /**
     * Insertion order kept deliberately. These reach a JSONB column, and
     * {@code Map.copyOf} iterates in an order salted per JVM run — two runs of
     * one input would store two different traces and read as a flake.
     */
    public RewriteTally {
        callsByPrompt = Collections.unmodifiableMap(new LinkedHashMap<>(callsByPrompt));
        refusals = refusals.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new EnumMap<>(refusals));
        if (unreachable < 0) {
            throw new IllegalArgumentException("unreachable is never negative");
        }
    }

    /** Faz D did not run, or ran without calling anything. */
    public static RewriteTally none() {
        return NONE;
    }

    public boolean isEmpty() {
        return callsByPrompt.isEmpty() && refusals.isEmpty() && unreachable == 0;
    }

    /**
     * This and another, added.
     *
     * <p>Faz D runs once per turn of the compile loop, and a document that came
     * out too long has already paid for the calls of the attempt before. The
     * trace is a record of what was spent, so the counts accumulate the way the
     * bill does.
     */
    public RewriteTally plus(RewriteTally other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        var calls = new LinkedHashMap<>(callsByPrompt);
        other.callsByPrompt.forEach((prompt, count) -> calls.merge(prompt, count, Integer::sum));
        var issues = new EnumMap<RewriteIssue, Integer>(RewriteIssue.class);
        issues.putAll(refusals);
        other.refusals.forEach((issue, count) -> issues.merge(issue, count, Integer::sum));
        return new RewriteTally(calls, issues, unreachable + other.unreachable);
    }
}
