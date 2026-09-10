package com.mustafatetik.atomcv.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

/**
 * The counting a prompt eval rests on (Bolum 53.4, 53.5).
 *
 * <p>The machinery rather than the model: what is checked is that a rate means
 * what it says, that a threshold is the specification's, and that the one
 * metric with zero tolerance is treated differently from the ones that are
 * quality bars. None of it costs a call.
 */
class EvalReportTest {

    @Test
    void arateIsHeldOverObserved() {
        var report = new EvalReport();
        report.record(EvalThresholds.NUMBERS_PRESERVED, true);
        report.record(EvalThresholds.NUMBERS_PRESERVED, true);
        report.record(EvalThresholds.NUMBERS_PRESERVED, false);
        report.record(EvalThresholds.NUMBERS_PRESERVED, true);

        assertThat(report.rate(EvalThresholds.NUMBERS_PRESERVED)).isEqualTo(0.75);
        assertThat(report.observed(EvalThresholds.NUMBERS_PRESERVED)).isEqualTo(4);
    }

    /**
     * <strong>A metric nothing observed is not zero and not one.</strong> A
     * suite that skipped a check has measured nothing, and reporting that as
     * total failure — or as success — would both be inventing a measurement.
     */
    @Test
    void ametricNobodyObservedIsRefusedRatherThanAnswered() {
        var report = new EvalReport();

        assertThatIllegalStateException()
                .isThrownBy(() -> report.rate(EvalThresholds.NUMBERS_PRESERVED));
        assertThat(report.has(EvalThresholds.NUMBERS_PRESERVED)).isFalse();
    }

    /** Both answers are recorded, or a suite that only logged failures reads as perfect. */
    @Test
    void afailureOnlyRunIsZeroRatherThanEmpty() {
        var report = new EvalReport();
        report.record(EvalThresholds.NO_NEW_TECHNOLOGIES, false);
        report.record(EvalThresholds.NO_NEW_TECHNOLOGIES, false);

        assertThat(report.rate(EvalThresholds.NO_NEW_TECHNOLOGIES)).isZero();
        assertThat(report.observed(EvalThresholds.NO_NEW_TECHNOLOGIES)).isEqualTo(2);
    }

    /** A table whose rows moved between two readings makes a diff meaningless. */
    @Test
    void themetricsKeepTheOrderTheyWereRecordedIn() {
        var report = new EvalReport();
        report.record(EvalThresholds.LENGTH_WITHIN_BOUNDS, true);
        report.record(EvalThresholds.NUMBERS_PRESERVED, true);
        report.record(EvalThresholds.NO_NEW_TECHNOLOGIES, true);

        assertThat(report.observations().keySet())
                .containsExactly(EvalThresholds.LENGTH_WITHIN_BOUNDS,
                        EvalThresholds.NUMBERS_PRESERVED,
                        EvalThresholds.NO_NEW_TECHNOLOGIES);
    }

    // ── thresholds (Bolum 53.5) ───────────────────────────────────────────

    @Test
    void thefloorsAreTheOnesTheSpecificationSets() {
        assertThat(EvalThresholds.floorFor(EvalThresholds.NUMBERS_PRESERVED)).isEqualTo(0.98);
        assertThat(EvalThresholds.floorFor(EvalThresholds.ENTITIES_PRESERVED)).isEqualTo(0.98);
        assertThat(EvalThresholds.floorFor(EvalThresholds.SCHEMA_CONFORMS)).isEqualTo(0.99);
        assertThat(EvalThresholds.floorFor(EvalThresholds.REQUIRED_SKILLS_FOUND)).isEqualTo(0.90);
    }

    /**
     * <strong>Zero tolerance is a rate of exactly one.</strong> A CV that
     * claims a skill because a posting asked for it is not a better CV, it is
     * a false one — so this is the product's promise rather than a quality
     * bar, and 99.9% is a failure.
     */
    @Test
    void oneInventedTechnologyIsAblockerAndNotAlowScore() {
        assertThat(EvalThresholds.floorFor(EvalThresholds.NO_NEW_TECHNOLOGIES)).isEqualTo(1.0);
        assertThat(EvalThresholds.isBlocking(EvalThresholds.NO_NEW_TECHNOLOGIES)).isTrue();

        assertThat(EvalThresholds.verdictFor(EvalThresholds.NO_NEW_TECHNOLOGIES, 0.999))
                .isEqualTo(EvalThresholds.Verdict.BLOCKED);
        assertThat(EvalThresholds.verdictFor(EvalThresholds.NO_NEW_TECHNOLOGIES, 1.0))
                .isEqualTo(EvalThresholds.Verdict.PASSED);
    }

    /** Everything else under its floor is worth a conversation, not a refusal. */
    @Test
    void aqualityBarUnderItsFloorIsLowRatherThanBlocked() {
        assertThat(EvalThresholds.verdictFor(EvalThresholds.NUMBERS_PRESERVED, 0.90))
                .isEqualTo(EvalThresholds.Verdict.BELOW);
        assertThat(EvalThresholds.isBlocking(EvalThresholds.NUMBERS_PRESERVED)).isFalse();
    }

    /**
     * A suite measuring something nobody set a bar for is measuring nothing:
     * the number would be printed, read as fine, and mean nothing.
     */
    @Test
    void ametricWithNoThresholdIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EvalThresholds.floorFor("vibes"));
    }

    @Test
    void thereportNamesTheMetricTheRateAndTheFloor() {
        var report = new EvalReport();
        report.record(EvalThresholds.NUMBERS_PRESERVED, true);
        report.record(EvalThresholds.NUMBERS_PRESERVED, false);

        String rendered = report.render("bullet_rewrite", "v1");

        assertThat(rendered)
                .contains("bullet_rewrite")
                .contains("numbers_preserved")
                .contains("50.0%")
                .contains("98%")
                .contains("2 cases");
    }
}
