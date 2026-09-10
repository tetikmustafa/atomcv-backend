package com.mustafatetik.atomcv.compilation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractLatexTest;
import com.mustafatetik.atomcv.rendering.measurement.CalibrationService;
import com.mustafatetik.atomcv.rendering.measurement.CapacityEstimator;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.HexColor;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Whether the estimate is safe, measured rather than assumed (Bolum 33.3).
 *
 * <p>Bolum 33.3 says "estimate plus 8%" and this is the only thing that can
 * say whether eight is the right number. Without it {@code SAFE_BUDGET} would
 * be a constant copied out of a specification into a file, and the first
 * person to move a slider would be the one finding out.
 *
 * <p><strong>The property under test is one-sided.</strong> An estimate that
 * is too generous over-fills a page, which is the failure the whole
 * measurement subsystem exists to prevent. One that is too mean prints a CV
 * with room to spare, which nobody notices. So what is asserted is not
 * accuracy but direction: what an estimated run allows itself must be less
 * than what the page really holds.
 */
@Tag("latex")
class CapacityEstimatorIT extends AbstractLatexTest {

    @Autowired
    private CalibrationService calibration;

    @Test
    void abiggerFontIsEstimatedSafely() {
        assertSafeFor(classicAt(12.0, 0.5, 1.0));
    }

    @Test
    void asmallerFontIsEstimatedSafely() {
        assertSafeFor(classicAt(9.0, 0.5, 1.0));
    }

    @Test
    void awiderMarginIsEstimatedSafely() {
        assertSafeFor(classicAt(11.0, 0.55, 1.0));
    }

    /**
     * <strong>And a margin wider than the calibration document can survive is
     * estimated forever, which is safe and is a real limit.</strong>
     *
     * <p>Past about 0.6in the probe document runs off its page and
     * {@code CalibrationService} refuses the reading rather than storing a
     * mismeasurement. Everything above that in the slider's range — it goes to
     * 1.0in — therefore never becomes exact: those CVs are made against the
     * estimate with its margin, every time.
     *
     * <p>Safe, because the estimate is only ever too mean. Not free: the page
     * is spent at 92% permanently, and the safety of the estimate at those
     * settings cannot be checked the way it is checked here, because there is
     * no measurement to check it against. Recorded rather than hidden.
     */
    @Test
    void amarginTooWideToCalibrateStaysEstimated() {
        var wide = classicAt(11.0, 0.8, 1.0);

        assertThat(calibration.measure(wide)).isEmpty();
        assertThat(CapacityEstimator.estimate(wide)).isPresent();
    }

    @Test
    void looserLeadingIsEstimatedSafely() {
        assertSafeFor(classicAt(11.0, 0.5, 1.25));
    }

    @Test
    void everyKnobAtOnceIsEstimatedSafely() {
        assertSafeFor(classicAt(9.5, 0.55, 1.15));
    }

    /**
     * And the estimate is not merely safe by being absurd: a margin that threw
     * away half the page would pass the assertion above and make the feature
     * useless. Eight percent is the most it is allowed to waste.
     */
    @Test
    void theEstimateIsCloseEnoughToBeWorthHaving() {
        var moved = classicAt(12.0, 0.5, 1.0);
        CapacityModel measured = calibration.measure(moved).orElseThrow();
        CapacityModel estimated = CapacityEstimator.estimate(moved).orElseThrow();

        double budget = estimated.pageTextHeightPt() * CapacityEstimator.SAFE_BUDGET;

        assertThat(budget / measured.pageTextHeightPt())
                .as("an estimated run still uses most of the page")
                .isGreaterThan(0.85);
    }

    /**
     * The safety property, on the number that decides how much goes on the
     * page: what the run allows itself against what the compiler says is
     * there.
     */
    private void assertSafeFor(TemplateCustomization customization) {
        CapacityModel measured = calibration.measure(customization).orElseThrow();
        CapacityModel estimated = CapacityEstimator.estimate(customization).orElseThrow();

        double budget = estimated.pageTextHeightPt() * CapacityEstimator.SAFE_BUDGET;

        assertThat(budget)
                .as("an estimated run never claims more page than there is")
                .isLessThan(measured.pageTextHeightPt());

        // And a line is charged at least what it costs, which is the other
        // half of not over-filling: the page could be the right height and
        // every bullet on it under-priced.
        assertThat(estimated.itemBaselineSkipPt() * CapacityEstimator.SAFE_BUDGET)
                .as("a bullet is not under-priced once the margin is spent")
                .isLessThan(measured.itemBaselineSkipPt());
    }

    private static TemplateCustomization classicAt(
            double fontSizePt, double marginInches, double lineSpacing) {

        return new TemplateCustomization("classic", FontFamily.MODERN,
                fontSizePt, marginInches, lineSpacing, HexColor.of("000000"));
    }
}
