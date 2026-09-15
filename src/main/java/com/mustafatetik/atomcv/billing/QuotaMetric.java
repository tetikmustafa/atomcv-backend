package com.mustafatetik.atomcv.billing;

import java.util.Locale;

/**
 * What is being counted. Stored in {@code usage_counters.metric}.
 *
 * <p><strong>Two counters, not one, and the reason is explicit:</strong> with
 * a single limit somebody could spend the whole of it on profile extraction —
 * the most expensive call the product makes — without ever generating a CV.
 * Separate metrics mean the expensive thing has its own, smaller ceiling.
 */
public enum QuotaMetric {

    GENERATION,

    PROFILE_EXTRACT,

    /** Not a count but a sum; the anomaly detector reads it. */
    LLM_COST;

    /** The column holds lowercase, like every other vocabulary here. */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
