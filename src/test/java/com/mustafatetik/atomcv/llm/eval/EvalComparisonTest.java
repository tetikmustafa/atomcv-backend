package com.mustafatetik.atomcv.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Two prompt versions, and what the difference is allowed to be (Bolum 53.6).
 *
 * <p>The distinction under test is the one a table cannot make on its own: a
 * blocker refuses whatever the predecessor did, and a regression is a
 * judgement about the change rather than about the number.
 */
class EvalComparisonTest {

    @Test
    void animprovementOnEveryMetricShips() {
        var comparison = comparing(
                report(0.98, 0.98, 1.00),
                report(0.99, 0.99, 1.00));

        assertThat(comparison.shipsWithoutQuestion()).isTrue();
        assertThat(comparison.render()).contains("No blocker and nothing regressed.");
    }

    /**
     * <strong>A blocker refuses whatever came before it.</strong> A prompt
     * that invents a technology does not ship because the old one invented one
     * too — the bar is the product's promise, not a comparison.
     */
    @Test
    void aninventedTechnologyBlocksEvenWhenItImproved() {
        var comparison = comparing(
                report(0.99, 0.99, 0.95),
                report(0.99, 0.99, 0.99));

        assertThat(comparison.regressed(EvalThresholds.NO_NEW_TECHNOLOGIES))
                .as("it got better")
                .isFalse();
        assertThat(comparison.shipsWithoutQuestion())
                .as("and still does not ship")
                .isFalse();
        assertThat(comparison.render()).contains("BLOCKER");
    }

    /**
     * And a metric that fell is a regression even while clearing its floor:
     * 98.1 to 97.2 is above no bar in particular and is still the change
     * making something worse.
     */
    @Test
    void ametricThatFellIsAregressionEvenAboveItsFloor() {
        var comparison = comparing(
                report(0.99, 0.995, 1.00),
                report(0.99, 0.985, 1.00));

        assertThat(comparison.regressed(EvalThresholds.ENTITIES_PRESERVED)).isTrue();
        assertThat(comparison.shipsWithoutQuestion()).isFalse();
        assertThat(comparison.render()).contains("regressed");
    }

    /** A metric the older version never measured is new, not a regression. */
    @Test
    void ametricTheOlderVersionNeverMeasuredIsNew() {
        var before = new EvalReport();
        before.record(EvalThresholds.NUMBERS_PRESERVED, true);
        var after = new EvalReport();
        after.record(EvalThresholds.NUMBERS_PRESERVED, true);
        after.record(EvalThresholds.LENGTH_WITHIN_BOUNDS, true);

        var comparison = new EvalComparison("bullet_rewrite", "v1", before, "v2", after);

        assertThat(comparison.regressed(EvalThresholds.LENGTH_WITHIN_BOUNDS)).isFalse();
        assertThat(comparison.shipsWithoutQuestion()).isTrue();
        assertThat(comparison.render()).contains("new");
    }

    @Test
    void thereportNamesBothVersionsAndTheDelta() {
        String rendered = comparing(report(0.98, 0.98, 1.00), report(0.99, 0.98, 1.00)).render();

        assertThat(rendered)
                .contains("bullet_rewrite")
                .contains("v1")
                .contains("v2")
                .contains("+1.0");
    }

    private static EvalComparison comparing(EvalReport before, EvalReport after) {
        return new EvalComparison("bullet_rewrite", "v1", before, "v2", after);
    }

    /** A run at these three rates, as whole cases rather than fractions. */
    private static EvalReport report(double numbers, double entities, double clean) {
        var report = new EvalReport();
        record(report, EvalThresholds.NUMBERS_PRESERVED, numbers);
        record(report, EvalThresholds.ENTITIES_PRESERVED, entities);
        record(report, EvalThresholds.NO_NEW_TECHNOLOGIES, clean);
        return report;
    }

    private static void record(EvalReport report, String metric, double rate) {
        int held = (int) Math.round(rate * 1000);
        for (int i = 0; i < 1000; i++) {
            report.record(metric, i < held);
        }
    }
}
