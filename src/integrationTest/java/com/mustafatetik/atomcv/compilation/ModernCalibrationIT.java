package com.mustafatetik.atomcv.compilation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractLatexTest;
import com.mustafatetik.atomcv.rendering.measurement.CalibrationService;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.HexColor;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Modern's stored capacity, re-derived from the compiler (Bolum 26.4, 33.5).
 *
 * <p>Shorter than the other two calibration classes because it does not have
 * to be long any more: {@code CalibrationService} derives the seventeen
 * numbers in the product now, and {@code CalibrationServiceIT} is what says
 * that derivation is right. What is left here is the question this class is
 * actually for — whether the numbers written down next to the preamble are
 * still the numbers the preamble produces.
 */
@Tag("latex")
class ModernCalibrationIT extends AbstractLatexTest {

    private static final Offset<Double> A_POINT = Offset.offset(0.01);

    @Autowired
    private CalibrationService calibration;

    @Test
    void everyStoredNumberIsWhatTheCompilerSays() {
        CapacityModel measured =
                calibration.measure(TemplateCustomization.MODERN).orElseThrow();
        CapacityModel stored = capacity(TemplateCustomization.MODERN);

        assertThat(measured.pageTextHeightPt()).isCloseTo(stored.pageTextHeightPt(), A_POINT);
        assertThat(measured.textWidthPt()).isCloseTo(stored.textWidthPt(), A_POINT);
        assertThat(measured.baselineSkipPt()).isCloseTo(stored.baselineSkipPt(), A_POINT);
        assertThat(measured.itemBaselineSkipPt())
                .isCloseTo(stored.itemBaselineSkipPt(), A_POINT);
        for (String name : stored.fixedCosts().keySet()) {
            assertThat(measured.fixedCost(name)).as(name)
                    .isCloseTo(stored.fixedCost(name), A_POINT);
        }
    }

    /**
     * The room is the preamble's, which is what makes this a template rather
     * than a preset somebody could have set from the sliders.
     */
    @Test
    void itisRoomierThanClassicWhereTheSlidersCannotReach() {
        CapacityModel modern = capacity(TemplateCustomization.MODERN);
        CapacityModel classic = capacity(TemplateCustomization.CLASSIC);

        assertThat(modern.itemBaselineSkipPt())
                .as("the same 11pt line: the room is not the leading")
                .isCloseTo(classic.itemBaselineSkipPt(), A_POINT);
        assertThat(modern.fixedCost(CapacityModel.ITEM_LINE))
                .as("but a bullet costs two points more")
                .isGreaterThan(classic.fixedCost(CapacityModel.ITEM_LINE));
        assertThat(modern.fixedCost(CapacityModel.SECTION_HEADER))
                .as("and a heading seven, which is its halved negative leading")
                .isGreaterThan(classic.fixedCost(CapacityModel.SECTION_HEADER));
    }

    /** Bolum 33.5 asks for about fifty lines against classic's fifty-four. */
    @Test
    void thepageHoldsAboutFiftyLines() {
        CapacityModel modern = capacity(TemplateCustomization.MODERN);

        double lines = modern.pageTextHeightPt() / modern.fixedCost(CapacityModel.ITEM_LINE);

        assertThat(lines).isBetween(48.0, 54.0);
        assertThat(lines)
                .as("and fewer than classic holds")
                .isLessThan(capacity(TemplateCustomization.CLASSIC).pageTextHeightPt()
                        / capacity(TemplateCustomization.CLASSIC)
                                .fixedCost(CapacityModel.ITEM_LINE));
    }

    /**
     * <strong>A geometry too roomy to calibrate is refused, not guessed.</strong>
     *
     * <p>This is modern's own first draft: a 0.6in margin at 1.05 leading, which
     * ran the calibration document eleven points past its page.
     * {@code \pagetotal} counts the page it is on, so the reading after the
     * break came back small and a project heading measured −646.7pt. Stored,
     * that would charge a piece of furniture as a credit and over-fill every
     * page at those settings with nothing saying so.
     *
     * <p>Empty is the right answer: the caller falls back to the estimate with
     * its margin and the person still gets a CV.
     */
    @Test
    void ageometryTooRoomyToCalibrateIsRefusedRatherThanMismeasured() {
        var tooRoomy = new TemplateCustomization("modern", FontFamily.MODERN,
                11.0, 0.6, 1.05, HexColor.of("1D4ED8"));

        assertThat(calibration.measure(tooRoomy)).isEmpty();
    }

    private static CapacityModel capacity(TemplateCustomization customization) {
        return TemplateRegistry.capacityOf(customization).orElseThrow();
    }
}
