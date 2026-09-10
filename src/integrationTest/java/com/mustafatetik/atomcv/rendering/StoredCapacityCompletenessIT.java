package com.mustafatetik.atomcv.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.rendering.measurement.Capacities;
import com.mustafatetik.atomcv.rendering.repository.MeasuredCapacities;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.HexColor;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A stored capacity outlives the model that wrote it (Bolum 33.1).
 *
 * <p>Layer B writes a row per geometry and reads it back for months. When the
 * capacity model grows a piece of furniture — as it did on 2026-09-10, when a
 * section heading turned out to cost ten points more in compact where a list
 * closed above it — every row already in the table is missing that number.
 *
 * <p>Charging a missing cost as zero is the worst of the options: it is
 * invisible, it always errs towards over-filling, and the page it overflows is
 * a page somebody sends to an employer. So a short row is treated as one nobody
 * has measured, which is a path that already exists and already has a margin.
 */
class StoredCapacityCompletenessIT extends AbstractIntegrationTest {

    /** A geometry with no built-in capacity, so the answer has to come from the table. */
    private static final TemplateCustomization MOVED = new TemplateCustomization(
            "classic", FontFamily.SANS, 9.5, 0.65, 1.25, HexColor.of("000000"));

    @Autowired
    private Capacities capacities;

    @Autowired
    private MeasuredCapacities measured;

    @Test
    void acompleteRowIsFound() {
        measured.store(MOVED.costKey(), complete());

        assertThat(capacities.find(MOVED)).isPresent();
    }

    @Test
    void arowMeasuredBeforeTheModelGrewIsTreatedAsNeverMeasured() {
        measured.store(MOVED.costKey(), missing(CapacityModel.SECTION_HEADER_AFTER_LIST));

        assertThat(capacities.find(MOVED))
                .as("a capacity that cannot answer every question is not an answer")
                .isEmpty();
    }

    /**
     * And the fallback is the estimate rather than a failure, so the person
     * still gets a CV while the measurement is asked for again.
     */
    @Test
    void theshortRowStillResolvesToAnEstimate() {
        measured.store(MOVED.costKey(), missing(CapacityModel.SECTION_HEADER_AFTER_LIST));

        var resolved = capacities.resolve(MOVED).orElseThrow();

        assertThat(resolved.estimated()).isTrue();
        assertThat(resolved.budgetFactor()).isLessThan(1.0);
    }

    private static CapacityModel complete() {
        return TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();
    }

    private static CapacityModel missing(String cost) {
        CapacityModel full = complete();
        var costs = new LinkedHashMap<>(full.fixedCosts());
        costs.remove(cost);
        return new CapacityModel(full.pageTextHeightPt(), full.textWidthPt(),
                full.baselineSkipPt(), full.itemBaselineSkipPt(), costs);
    }
}
