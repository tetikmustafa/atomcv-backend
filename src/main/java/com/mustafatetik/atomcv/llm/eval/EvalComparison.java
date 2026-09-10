package com.mustafatetik.atomcv.llm.eval;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Two prompt versions, side by side (Bolum 53.6).
 *
 * <p><strong>The delta is what decides, not the rate.</strong> A version
 * holding numbers at 99.2% is neither good nor bad on its own; the same
 * version against a predecessor at 99.5% is a regression, and against one at
 * 97% it is the reason to ship. A single run's table cannot say either.
 *
 * <p>Nothing here compares text. What is compared is what
 * {@link EvalReport} counted: a property either held for a case or it did not,
 * and the difference between two rates is a difference between two prompts
 * rather than between two vocabularies a model happened to pick.
 */
public final class EvalComparison {

    private final String promptId;
    private final String before;
    private final String after;
    private final EvalReport baseline;
    private final EvalReport candidate;

    public EvalComparison(String promptId, String before, EvalReport baseline,
            String after, EvalReport candidate) {

        this.promptId = promptId;
        this.before = before;
        this.after = after;
        this.baseline = baseline;
        this.candidate = candidate;
    }

    /**
     * Whether the candidate may ship.
     *
     * <p><strong>Two separate refusals, and they are not the same
     * question.</strong> A blocking metric under its floor is a no whatever
     * the predecessor did — a prompt that invents a technology does not ship
     * because the old one invented one too. A non-blocking metric that fell
     * is a regression, and that is a judgement about the change rather than
     * about the number.
     */
    public boolean shipsWithoutQuestion() {
        for (String metric : metrics()) {
            if (!candidate.has(metric)) {
                continue;
            }
            if (EvalThresholds.verdictFor(metric, candidate.rate(metric))
                    == EvalThresholds.Verdict.BLOCKED) {
                return false;
            }
            if (regressed(metric)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a metric went down at all, against the version before it. */
    public boolean regressed(String metric) {
        if (!baseline.has(metric) || !candidate.has(metric)) {
            return false;
        }
        return candidate.rate(metric) < baseline.rate(metric);
    }

    /**
     * The table a person reads before merging a prompt.
     *
     * <p>Both columns and the difference, because a reader deciding on a
     * change needs all three: the floor says whether it is allowed, the delta
     * says whether it got worse, and only one of those is a blocker.
     */
    public String render() {
        var out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "PROMPT EVAL — %s: %s → %s%n",
                promptId, before, after));
        out.append("═".repeat(56)).append(System.lineSeparator());
        out.append(String.format(Locale.ROOT, "%-26s %7s %7s %7s  %s%n",
                "Metric", before, after, "Δ", ""));
        out.append("─".repeat(56)).append(System.lineSeparator());

        for (String metric : metrics()) {
            if (!candidate.has(metric)) {
                continue;
            }
            double now = candidate.rate(metric);
            var verdict = EvalThresholds.verdictFor(metric, now);
            if (!baseline.has(metric)) {
                out.append(String.format(Locale.ROOT, "%-26s %6s %6.1f%% %7s  %s%n",
                        metric, "—", now * 100, "new", verdict.symbol()));
                continue;
            }
            double was = baseline.rate(metric);
            out.append(String.format(Locale.ROOT, "%-26s %6.1f%% %6.1f%% %+6.1f  %s%s%n",
                    metric, was * 100, now * 100, (now - was) * 100,
                    verdict.symbol(),
                    verdict == EvalThresholds.Verdict.PASSED && now < was ? "  regressed" : ""));
        }
        out.append("─".repeat(56)).append(System.lineSeparator());
        out.append(shipsWithoutQuestion()
                ? "No blocker and nothing regressed."
                : "Do not ship without a reason: a blocker or a regression above.");
        out.append(System.lineSeparator());
        return out.toString();
    }

    /** Every metric either run measured, in the order they were first seen. */
    private Set<String> metrics() {
        var metrics = new LinkedHashSet<>(baseline.observations().keySet());
        metrics.addAll(candidate.observations().keySet());
        return metrics;
    }
}
