package com.mustafatetik.atomcv.llm.gateway;

/**
 * Somewhere to put an answer a real provider gave (Bolum 54.2).
 *
 * <p>The seam exists because recording cannot ride on
 * {@code LlmInvocationEvent}: that event carries a shape and never content,
 * deliberately and permanently (absolute rule 4). A recorder needs the answer
 * itself, so it is a separate collaborator the chain calls on its way out —
 * and one that is absent in every profile but {@code local-record}.
 *
 * <p>Only successes are offered. A failure has no answer to keep, and a
 * schema mismatch has one that would replay as the same mismatch forever.
 */
public interface AnswerRecorder {

    /**
     * @param request the call that was made, which names where the answer goes
     * @param answer  the parsed value, re-serialised by the implementation
     */
    void record(StructuredRequest<?> request, Object answer);

    /**
     * Takes back a recording the pipeline went on to refuse.
     *
     * <p>The chain records on its way out, which is the only place the answer
     * and the request that earned it are both in scope — but it is upstream of
     * every gate, so a refused answer was being kept and replayed. Bolum 18.4
     * already reasons this out for the cache it sits beside: <em>caching a
     * refusal would freeze it for a week, and a model that wandered once
     * should be asked again</em>. A recorded refusal is worse than a frozen
     * one, because it never expires — it becomes a fixture that fails the same
     * way on every clone, and looks like a broken pipeline rather than a bad
     * recording.
     *
     * <p>Only Bolum 18.4's gate calls this, and the scope is deliberate: it is
     * the one refusal that ends the job. The validators below it (Bolum 21.4,
     * the About and cover-letter checks) keep the original when they reject, so
     * replaying an answer they refuse produces exactly what no fixture at all
     * produces. There is nothing to withdraw.
     */
    void discard(StructuredRequest<?> request);
}
