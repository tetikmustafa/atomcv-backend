package com.mustafatetik.atomcv.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractLatexTest;
import com.mustafatetik.atomcv.rendering.measurement.CalibrationService;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.HexColor;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * What {@code scripts/measure-template.sh} runs.
 *
 * <p><strong>A tool with a test's shape, and it is not pretending.</strong>
 * The checklist for adding a template has two boxes that say "measured" — the
 * page capacity and the fixed costs — and until this existed the only way to
 * tick them was to read a number out of a failing assertion somewhere else.
 * The repository layout names {@code scripts/measure-template.sh}; this is the
 * half of it that can reach a compiler.
 *
 * <p>It asserts what a measurement must be true of — a page has positive
 * height, a line has a height, no piece of furniture is a page tall in either
 * direction — and <strong>prints the rest</strong>, because the numbers are
 * the output and a person is the reader. The third rule about testing is not
 * to call a suite green; this is the other direction, a run whose value is its
 * report.
 *
 * <p>Nothing is written. A measured capacity reaches the database through
 * {@code MeasurementJobHandler} when somebody actually renders at that
 * geometry; a script run is a question, not a decision.
 */
@Tag("latex")
class TemplateMeasurementRunIT extends AbstractLatexTest {

    @Autowired
    private CalibrationService calibration;

    @Test
    void measureAndReport() {
        TemplateCustomization asked = asked();
        CapacityModel measured = calibration.measure(asked)
                .orElseThrow(() -> new AssertionError(
                        "The calibration document did not compile for " + asked.costKey()
                        + ". An empty capacity is not a capacity of zero -- a page "
                        + "guarantee made against numbers nobody produced is not a "
                        + "guarantee."));

        report(asked, measured);

        assertThat(measured.pageTextHeightPt())
                .as("a page holds something")
                .isGreaterThan(100);
        assertThat(measured.baselineSkipPt())
                .as("a line of body text has a height")
                .isGreaterThan(1);
        // **A fixed cost may be negative, and this run found two that are.**
        // A preamble that pulls back before a heading spends a negative amount
        // of page there -- TemplateRegistry's own constants carry the same
        // sign for the same reason. What cannot happen is furniture whose cost
        // is a page in either direction: that is a probe that measured
        // something other than the piece it named.
        assertThat(measured.fixedCosts().values())
                .as("every piece of furniture costs less than a page, in either direction")
                .allSatisfy(cost -> assertThat(Math.abs(cost))
                        .isLessThan(measured.pageTextHeightPt()));
    }

    /**
     * The geometry named on the command line, or the template's own defaults.
     *
     * <p>System properties rather than arguments, because the measurement runs
     * in a test JVM and {@code -D} is the channel Gradle already forwards --
     * the same one {@code -Dgolden.record=true} uses.
     */
    private static TemplateCustomization asked() {
        String templateId = System.getProperty("measure.template", "classic");
        if (!TemplateRegistry.ids().contains(templateId)) {
            throw new AssertionError("No template called " + templateId
                    + ". The registry has " + new TreeMap<>(
                            TemplateRegistry.ids().stream().collect(
                                    java.util.stream.Collectors.toMap(id -> id, id -> ""))).keySet());
        }
        TemplateCustomization defaults = TemplateRegistry.defaultsFor(templateId);

        return new TemplateCustomization(
                templateId,
                property("measure.font") == null
                        ? defaults.fontFamily()
                        : FontFamily.fromWireValue(property("measure.font")),
                doubleProperty("measure.size", defaults.fontSizePt()),
                doubleProperty("measure.margin", defaults.marginInches()),
                doubleProperty("measure.spacing", defaults.lineSpacing()),
                property("measure.accent") == null
                        ? defaults.accentColor()
                        : HexColor.of(property("measure.accent")));
    }

    /**
     * Printed rather than asserted, and sorted: this is what somebody copies
     * into {@code TemplateRegistry} after changing a preamble, and a report
     * whose lines move between runs is one nobody can diff (CLAUDE.md's note
     * about salted iteration order).
     */
    private static void report(TemplateCustomization asked, CapacityModel measured) {
        System.out.println();
        System.out.println("  " + asked.costKey()
                + "  font=" + asked.fontFamily().wireValue()
                + "  size=" + asked.fontSizePt()
                + "  margin=" + asked.marginInches()
                + "  spacing=" + asked.lineSpacing());
        System.out.printf("  %-28s %10.4f%n", "pageTextHeightPt", measured.pageTextHeightPt());
        System.out.printf("  %-28s %10.4f%n", "baselineSkipPt", measured.baselineSkipPt());
        for (Map.Entry<String, Double> cost : new TreeMap<>(measured.fixedCosts()).entrySet()) {
            System.out.printf("  %-28s %10.4f%n", cost.getKey(), cost.getValue());
        }
        System.out.println();
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static double doubleProperty(String name, double fallback) {
        String value = property(name);
        return value == null ? fallback : Double.parseDouble(value);
    }
}
