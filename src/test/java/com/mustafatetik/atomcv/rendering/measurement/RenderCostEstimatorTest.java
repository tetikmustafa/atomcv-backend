package com.mustafatetik.atomcv.rendering.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import org.junit.jupiter.api.Test;

/**
 * The stand-in for a measurement (Bolum 26.5).
 *
 * <p>Its accuracy is checked against the real compiler in
 * {@code RenderCostMeasurementIT}; what is checked here is the shape it has to
 * have whatever the numbers turn out to be.
 */
class RenderCostEstimatorTest {

    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

    @Test
    void evenTheShortestBulletCostsALine() {
        double cost = estimate("Go");

        assertThat(cost).isGreaterThanOrEqualTo(2 * CAPACITY.baselineSkipPt());
    }

    @Test
    void moreTextCostsMore() {
        double shorter = estimate("Built ETL pipelines");
        double longer = estimate("Built ETL pipelines processing 300K+ rows a day and cut the "
                + "nightly window from six hours to fifty minutes across four teams");

        assertThat(longer).isGreaterThan(shorter);
    }

    @Test
    void theCostGrowsInWholeLines() {
        // Points, not lines, is how the budget is summed (Bolum 26.3) — but a
        // line break is a step, and an estimate that pretended otherwise would
        // be claiming a precision it does not have.
        double step = CAPACITY.baselineSkipPt() * RenderCostEstimator.SAFETY_MARGIN;

        assertThat(estimate("x".repeat(400)) - estimate("x".repeat(200)))
                .isCloseTo(2 * step, org.assertj.core.data.Offset.offset(step));
    }

    @Test
    void aNarrowerColumnCostsMoreLines() {
        var content = RichContent.plain("Built ETL pipelines processing 300K+ rows a day");

        double wide = RenderCostEstimator.estimatePt(
                content, TemplateCustomization.CLASSIC, CAPACITY, 500);
        double narrow = RenderCostEstimator.estimatePt(
                content, TemplateCustomization.CLASSIC, CAPACITY, 150);

        assertThat(narrow).isGreaterThan(wide);
    }

    @Test
    void aBulletIsChargedAtTheWidthABulletGets() {
        var content = RichContent.plain("Built ETL pipelines processing 300K+ rows a day, "
                + "cutting the nightly window to fifty minutes");

        double asBullet = RenderCostEstimator.estimateBulletPt(
                content, TemplateCustomization.CLASSIC, CAPACITY);
        double atFullWidth = RenderCostEstimator.estimatePt(
                content, TemplateCustomization.CLASSIC, CAPACITY, CAPACITY.textWidthPt());

        assertThat(asBullet).isGreaterThanOrEqualTo(atFullWidth);
    }

    @Test
    void theSafetyMarginIsInEveryAnswer() {
        // Bolum 26.5 asks for it explicitly, and an estimate without it would
        // be a coin toss on the page limit rather than a conservative guess.
        assertThat(RenderCostEstimator.SAFETY_MARGIN).isGreaterThan(1.0);
        assertThat(estimate("Go") % CAPACITY.baselineSkipPt()).isNotZero();
    }

    private static double estimate(String text) {
        return RenderCostEstimator.estimateBulletPt(
                RichContent.plain(text), TemplateCustomization.CLASSIC, CAPACITY);
    }
}
