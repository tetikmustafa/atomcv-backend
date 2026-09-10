package com.mustafatetik.atomcv.llm.eval;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a prompt did, counted rather than read (Bolum 53.4).
 *
 * <p><strong>Properties, never text.</strong> A model picks different words
 * every time, so comparing an answer to a stored one measures the weather. What
 * can be measured is whether a property held: did the numbers survive, did a
 * technology appear that the atom never claimed. Those are yes-or-no questions
 * over many cases, and a rate over them is a fact about a prompt.
 *
 * <p>Ordered, so two runs of one suite render one report — a table whose rows
 * moved between two readings would make a diff of the two meaningless
 * (CLAUDE.md).
 *
 * <p>Not a test framework. It counts, it computes rates, and it says whether a
 * rate cleared its threshold; deciding what to observe belongs to the suite,
 * and deciding what a failure means belongs to {@link EvalThresholds}.
 */
public final class EvalReport {

    private final Map<String, int[]> observations = new LinkedHashMap<>();

    /**
     * @param held whether the property held for this case. Both answers are
     *             recorded: a rate needs a denominator, and a suite that only
     *             recorded failures would report 0% as perfect
     */
    public void record(String metric, boolean held) {
        int[] counts = observations.computeIfAbsent(metric, name -> new int[2]);
        counts[1]++;
        if (held) {
            counts[0]++;
        }
    }

    /**
     * @return the share of cases where the property held, between 0 and 1
     * @throws IllegalStateException for a metric nothing was recorded under.
     *         Not zero: a metric with no observations is a suite that did not
     *         run the check, and reporting that as total failure — or as
     *         success — would both be inventing a measurement
     */
    public double rate(String metric) {
        int[] counts = observations.get(metric);
        if (counts == null || counts[1] == 0) {
            throw new IllegalStateException("Nothing was observed for " + metric);
        }
        return (double) counts[0] / counts[1];
    }

    public int observed(String metric) {
        int[] counts = observations.get(metric);
        return counts == null ? 0 : counts[1];
    }

    public boolean has(String metric) {
        return observed(metric) > 0;
    }

    /** The metrics this run recorded, in the order it recorded them. */
    public Map<String, int[]> observations() {
        var copy = new LinkedHashMap<String, int[]>();
        observations.forEach((metric, counts) -> copy.put(metric, counts.clone()));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * One run's numbers, as a table (Bolum 53.6).
     *
     * <p>The verdict column is the point of reading it: a rate on its own says
     * nothing without the threshold it is being held to, and the one thing a
     * person scanning this needs to find is the blocker.
     */
    public String render(String promptId, String version) {
        var out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "PROMPT EVAL — %s: %s%n", promptId, version));
        out.append("═".repeat(48)).append(System.lineSeparator());
        out.append(String.format(Locale.ROOT, "%-26s %8s %8s  %s%n",
                "Metric", "Rate", "Floor", ""));
        out.append("─".repeat(48)).append(System.lineSeparator());

        for (Map.Entry<String, int[]> entry : observations.entrySet()) {
            String metric = entry.getKey();
            double rate = rate(metric);
            var verdict = EvalThresholds.verdictFor(metric, rate);
            out.append(String.format(Locale.ROOT, "%-26s %7.1f%% %7s  %s  (%d cases)%n",
                    metric,
                    rate * 100,
                    EvalThresholds.describe(metric),
                    verdict.symbol(),
                    entry.getValue()[1]));
        }
        return out.toString();
    }
}
