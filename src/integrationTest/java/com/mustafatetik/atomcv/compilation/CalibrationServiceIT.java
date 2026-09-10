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
 * The runtime measurement path, checked against the numbers a person wrote
 * down (Bolum 26.4, 33.1).
 *
 * <p><strong>This is the test that lets layer B exist.</strong> Both stored
 * capacities were derived by hand from {@code LatexCalibrationIT}'s own
 * probes and typed into {@code TemplateRegistry}. If the service that will
 * measure a slider's geometry cannot reproduce those two exactly, then every
 * capacity it produces for a setting nobody has checked is worth nothing —
 * and nothing downstream would ever say so, because there is no second
 * opinion about a geometry only one thing has ever compiled.
 *
 * <p>So the two known answers are the contract. The derivation lives in the
 * service and again, independently, in the calibration tests: if either is
 * wrong they disagree, rather than agreeing about the same mistake.
 */
@Tag("latex")
class CalibrationServiceIT extends AbstractLatexTest {

    /**
     * The tolerance the calibration tests use. These are sums of measured
     * points, so comparing for equality would test IEEE 754's associativity
     * rather than the arithmetic.
     */
    private static final Offset<Double> A_POINT = Offset.offset(0.01);

    @Autowired
    private CalibrationService calibration;

    @Test
    void itreproducesTheClassicCapacityThatWasWrittenDown() {
        var measured = calibration.measure(TemplateCustomization.CLASSIC).orElseThrow();

        assertSameAs(TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow(),
                measured);
    }

    @Test
    void itreproducesTheCompactCapacityThatWasWrittenDown() {
        var measured = calibration.measure(TemplateCustomization.COMPACT).orElseThrow();

        assertSameAs(TemplateRegistry.capacityOf(TemplateCustomization.COMPACT).orElseThrow(),
                measured);
    }

    /**
     * And a geometry nobody has ever compiled produces a capacity anyway,
     * which is the whole point of layer B.
     *
     * <p>Asserted against arithmetic rather than against a stored answer,
     * because there is no stored answer: the text block is pure geometry, so a
     * margin two tenths wider than classic's is 28.8pt shorter and narrower —
     * and a page that came back the same size as classic's would mean the
     * customization never reached the document.
     */
    @Test
    void ameasuresAgeometryNobodyHasCompiledBefore() {
        var wider = new TemplateCustomization("classic", FontFamily.MODERN, 11.0, 0.7, 1.0,
                HexColor.of("000000"));

        var measured = calibration.measure(wider).orElseThrow();

        var classic = TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();
        // Two tenths of an inch at each of two edges is 28.8pt, and both
        // dimensions come back 28.9076 -- `geometry` does its own rounding on
        // the way to a text block. Asserted to within a point rather than to
        // the hundredth, because 28.8 is the prediction and 28.9076 is the
        // measurement, and this whole service exists because the second is the
        // one that can be trusted. Guessed at twice here before the compiler
        // was asked.
        assertThat(classic.pageTextHeightPt() - measured.pageTextHeightPt())
                .as("the page got shorter by about two tenths of an inch")
                .isCloseTo(28.8, Offset.offset(0.5));
        assertThat(classic.textWidthPt() - measured.textWidthPt())
                .as("and narrower by the same")
                .isCloseTo(28.8, Offset.offset(0.5));
        // The furniture is not simply scaled: it is measured, and a heading
        // still costs what a heading costs at this font size.
        assertThat(measured.fixedCost(CapacityModel.SECTION_HEADER))
                .isCloseTo(classic.fixedCost(CapacityModel.SECTION_HEADER), A_POINT);
    }

    /**
     * A bigger font makes every line taller, and that is the case layer B's
     * cost key exists for: the same atom charged the same points at 9pt and
     * 12pt would be the page guarantee failing quietly.
     */
    @Test
    void abiggerFontIsAtallerLine() {
        var larger = new TemplateCustomization("classic", FontFamily.MODERN, 12.0, 0.5, 1.0,
                HexColor.of("000000"));

        var measured = calibration.measure(larger).orElseThrow();
        var classic = TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

        assertThat(measured.itemBaselineSkipPt()).isGreaterThan(classic.itemBaselineSkipPt());
        assertThat(measured.fixedCost(CapacityModel.ITEM_LINE))
                .isGreaterThan(classic.fixedCost(CapacityModel.ITEM_LINE));
        assertThat(larger.costKey()).isNotEqualTo(TemplateCustomization.CLASSIC.costKey());
    }

    private static void assertSameAs(CapacityModel stored, CapacityModel measured) {
        assertThat(measured.pageTextHeightPt())
                .isCloseTo(stored.pageTextHeightPt(), A_POINT);
        assertThat(measured.textWidthPt()).isCloseTo(stored.textWidthPt(), A_POINT);
        assertThat(measured.baselineSkipPt()).isCloseTo(stored.baselineSkipPt(), A_POINT);
        assertThat(measured.itemBaselineSkipPt())
                .isCloseTo(stored.itemBaselineSkipPt(), A_POINT);

        for (String name : new String[] {
                CapacityModel.HEADER_BLOCK, CapacityModel.SECTION_HEADER,
                CapacityModel.ENTRY_HEADER, CapacityModel.ENTRY_HEADER_AFTER_LIST,
                CapacityModel.PROJECT_HEADING, CapacityModel.PROJECT_HEADING_AFTER_LIST,
                CapacityModel.ITEMIZE_OVERHEAD, CapacityModel.ITEM_LINE,
                CapacityModel.SECTION_ITEM_LINE, CapacityModel.SECTION_LIST_OVERHEAD,
                CapacityModel.SECTION_LIST_CLOSE, CapacityModel.PARAGRAPH_LIST_OVERHEAD,
                CapacityModel.INLINE_ROW, CapacityModel.INLINE_LIST_OVERHEAD}) {

            assertThat(measured.fixedCost(name))
                    .as(name)
                    .isCloseTo(stored.fixedCost(name), A_POINT);
        }
    }
}
