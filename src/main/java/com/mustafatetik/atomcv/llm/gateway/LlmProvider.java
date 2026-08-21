package com.mustafatetik.atomcv.llm.gateway;

/**
 * One vendor, behind one shape (Bolum 27.1).
 *
 * <p>Raw REST underneath, no vendor SDK: Bolum 5.4 keeps the abstraction here
 * rather than taking five dependencies that break on their own schedules.
 *
 * <p>The return type is {@link LlmOutcome} rather than Bolum 27.1's
 * {@code Result}. The difference is the failure type and it is deliberate —
 * see {@link LlmFailure}.
 */
public interface LlmProvider {

    /** Stable, lowercase, and the name a chain in configuration refers to. */
    String id();

    /**
     * Whether this provider is configured at all.
     *
     * <p>Bolum 27.3 skips an unavailable provider <em>silently</em> and does
     * not count it as tried: a chain listing five vendors on a deployment that
     * has one key is the normal case, not a degraded one.
     */
    boolean isAvailable();

    /** Which chain this provider belongs to. */
    ModelTier tier();

    <T> LlmOutcome<T> callStructured(StructuredRequest<T> request);
}
