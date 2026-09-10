package com.mustafatetik.atomcv.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.rendering.measurement.Capacities;
import com.mustafatetik.atomcv.rendering.measurement.CapacityEstimator;
import com.mustafatetik.atomcv.rendering.repository.MeasuredCapacities;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.HexColor;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A measured capacity against the real schema (V12, Bolum 33.1).
 *
 * <p>Seventeen numbers and a JSONB map, none of which schema validation checks
 * the shape of. What is being proved is that a capacity survives the round
 * trip intact — because a page guarantee is made against these, and a number
 * that came back wrong would over-fill a page rather than fail anything.
 */
class TemplateCapacityIT extends AbstractIntegrationTest {

    private static final TemplateCustomization MOVED = new TemplateCustomization(
            "classic", FontFamily.SANS, 9.5, 0.65, 1.25, HexColor.of("000000"));

    @Autowired
    private MeasuredCapacities capacities;

    @Autowired
    private Capacities lookup;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void startFromNothingMeasured() {
        jdbc.update("DELETE FROM template_capacities");
    }

    @Test
    void acapacitySurvivesTheRoundTripIntact() {
        capacities.store(MOVED.costKey(), aCapacity());

        CapacityModel read = capacities.find(MOVED.costKey()).orElseThrow();

        assertThat(read.pageTextHeightPt()).isEqualTo(700.5);
        assertThat(read.textWidthPt()).isEqualTo(520.25);
        assertThat(read.baselineSkipPt()).isEqualTo(11.75);
        assertThat(read.itemBaselineSkipPt()).isEqualTo(10.5);
        assertThat(read.fixedCost(CapacityModel.HEADER_BLOCK)).isEqualTo(55.5);
        assertThat(read.fixedCost(CapacityModel.INLINE_LIST_OVERHEAD)).isEqualTo(-4.25);
    }

    /** The key is the geometry, so this is the one thing a wrong row would be. */
    @Test
    void anothergeometryIsNotThisOne() {
        capacities.store(MOVED.costKey(), aCapacity());

        assertThat(capacities.find(TemplateCustomization.CLASSIC.costKey())).isEmpty();
    }

    /**
     * Measuring twice is not a conflict. The debounce lives in a browser and
     * does not bind a second tab, and the two answers are the same seventeen
     * numbers — a unique-key failure would fail a job for arriving second with
     * the right answer.
     */
    @Test
    void measuringTheSameGeometryTwiceReplacesRatherThanFails() {
        capacities.store(MOVED.costKey(), aCapacity());
        capacities.store(MOVED.costKey(), aCapacity());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM template_capacities", Integer.class)).isEqualTo(1);
    }

    // ── what Capacities does with it ──────────────────────────────────────

    @Test
    void ageometryNobodyHasMeasuredHasNoCapacity() {
        assertThat(lookup.find(MOVED)).isEmpty();
        assertThat(lookup.isMeasured(MOVED)).isFalse();
    }

    @Test
    void ameasuredGeometryBecomesUsable() {
        capacities.store(MOVED.costKey(), aCapacity());

        assertThat(lookup.find(MOVED)).isPresent();
        assertThat(lookup.isMeasured(MOVED)).isTrue();
    }

    // -- what resolve() does with an unmeasured geometry (Bolum 33.3) --------

    /**
     * A person who has just moved a slider is waiting and the measurement is a
     * compilation away. Bolum 33.3 prints them a CV rather than a spinner.
     */
    @Test
    void anunmeasuredGeometryIsEstimatedRatherThanRefused() {
        var resolved = lookup.resolve(MOVED).orElseThrow();

        assertThat(resolved.estimated()).isTrue();
        assertThat(resolved.capacity().pageTextHeightPt()).isPositive();
    }

    /** And it spends less of the page for it. */
    @Test
    void anestimatedRunSpendsLessOfThePage() {
        assertThat(lookup.resolve(MOVED).orElseThrow().budgetFactor())
                .isEqualTo(CapacityEstimator.SAFE_BUDGET)
                .isLessThan(1.0);
    }

    /** A measurement, once it exists, is spent in full and is not an estimate. */
    @Test
    void ameasuredGeometryIsSpentInFull() {
        capacities.store(MOVED.costKey(), aCapacity());

        var resolved = lookup.resolve(MOVED).orElseThrow();

        assertThat(resolved.estimated()).isFalse();
        assertThat(resolved.budgetFactor()).isEqualTo(1.0);
        assertThat(resolved.capacity().pageTextHeightPt()).isEqualTo(700.5);
    }

    /** A built-in template is measured by definition, so it is never estimated. */
    @Test
    void abuiltInTemplateIsNeverEstimated() {
        assertThat(lookup.resolve(TemplateCustomization.CLASSIC).orElseThrow().estimated())
                .isFalse();
        assertThat(lookup.resolve(TemplateCustomization.COMPACT).orElseThrow().estimated())
                .isFalse();
    }

    /**
     * <strong>The built-in constants win.</strong> Classic's numbers live next
     * to the preamble they describe and the calibration lane re-derives them on
     * every run; a row in this table is neither reviewed nor re-derived. If a
     * measurement ever drifted — a container on a different TeX Live, a
     * calibration that half-succeeded — it must not quietly replace the number
     * the tests are checking.
     */
    @Test
    void astoredRowCannotOverrideAbuiltInTemplate() {
        capacities.store(TemplateCustomization.CLASSIC.costKey(), aCapacity());

        assertThat(lookup.find(TemplateCustomization.CLASSIC).orElseThrow()
                .pageTextHeightPt())
                .isEqualTo(TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC)
                        .orElseThrow().pageTextHeightPt())
                .isNotEqualTo(700.5);
    }

    private static CapacityModel aCapacity() {
        var fixed = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> entry
                : TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC)
                        .orElseThrow().fixedCosts().entrySet()) {
            fixed.put(entry.getKey(), entry.getValue());
        }
        // Two of them moved, so a row that came back with classic's numbers
        // would be a row that was never written.
        fixed.put(CapacityModel.HEADER_BLOCK, 55.5);
        fixed.put(CapacityModel.INLINE_LIST_OVERHEAD, -4.25);
        return new CapacityModel(700.5, 520.25, 11.75, 10.5, fixed);
    }
}
