package com.mustafatetik.atomcv.llm.gateway;

/**
 * One vendor, behind one shape.
 *
 * <p>Raw REST underneath, no vendor SDK: the abstraction stays here rather
 * than taking five dependencies that break on their own schedules.
 *
 * <p>The return type is {@link LlmOutcome} rather than the {@code Result}. The
 * difference is the failure type and it is deliberate — see {@link
 * LlmFailure}.
 */
public interface LlmProvider {

    /** Stable, lowercase, and the name a chain in configuration refers to. */
    String id();

    /**
     * Whether this provider is configured at all.
     *
     * <p>An unavailable provider is skipped <em>silently</em> and is not
     * counted as tried: a chain listing five vendors on a deployment that has
     * one key is the normal case, not a degraded one.
     */
    boolean isAvailable();

    /** Which chain this provider belongs to. */
    ModelTier tier();

    <T> LlmOutcome<T> callStructured(StructuredRequest<T> request);
}
