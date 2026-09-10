package com.mustafatetik.atomcv.rendering.measurement;

import com.mustafatetik.atomcv.rendering.repository.MeasuredCapacities;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * What a page of this shape holds, from wherever it is known (Bolum 33.1).
 *
 * <p>{@link TemplateRegistry#capacityOf} answered this for the two templates
 * at their own settings and returned empty for everything else, which was the
 * honest answer while nothing could measure anything else. This is the same
 * question with the second half of the answer attached.
 *
 * <p><strong>The built-in constants are asked first, and that ordering is the
 * design.</strong> Classic's and compact's numbers live next to the preambles
 * they describe, are re-derived by the calibration tests on every run, and are
 * reviewed by a person when they change. A row in the database is none of
 * those things. If both hold a value for one key the constant wins, so a
 * measurement that drifted — a container on a different TeX Live, a
 * calibration that half-succeeded — cannot quietly replace a number the test
 * lane is checking.
 *
 * <p>Empty still means "nobody has measured this". It is not a capacity of
 * zero and must never be treated as one: a page guarantee made against numbers
 * nobody produced is not a guarantee. What turns empty into a document is
 * Bolum 33.3's estimate, and that is a decision for the caller rather than a
 * default hidden here.
 */
@Component
public class Capacities {

    private final MeasuredCapacities measured;

    Capacities(MeasuredCapacities measured) {
        this.measured = measured;
    }

    public Optional<CapacityModel> find(TemplateCustomization customization) {
        Optional<CapacityModel> builtIn = TemplateRegistry.capacityOf(customization);
        if (builtIn.isPresent()) {
            return builtIn;
        }
        return measured.find(customization.costKey());
    }

    /** Whether a generation could run at these settings without estimating. */
    public boolean isMeasured(TemplateCustomization customization) {
        return find(customization).isPresent();
    }

    /**
     * A capacity to select against, measured if anybody has, estimated if not
     * (Bolum 33.3).
     *
     * <p>This is where empty stops being an answer. A person who has just
     * moved a slider is waiting, the measurement is a compilation away, and
     * Bolum 33.3 chooses to print them a CV rather than a spinner — on an
     * estimate, spending less of the page.
     *
     * <p>Still empty for a template with no measured default of its own, which
     * would mean estimating from nothing.
     */
    public Optional<Resolved> resolve(TemplateCustomization customization) {
        Optional<CapacityModel> known = find(customization);
        if (known.isPresent()) {
            return known.map(capacity -> new Resolved(capacity, false));
        }
        return CapacityEstimator.estimate(customization)
                .map(capacity -> new Resolved(capacity, true));
    }

    /**
     * @param estimated whether these numbers came from a compiler or from
     *                  arithmetic. Worth carrying rather than hiding: it
     *                  decides how much of the page the run may spend, and it
     *                  is the honest thing to record in a trace
     */
    public record Resolved(CapacityModel capacity, boolean estimated) {

        /**
         * The share of the page this run allows itself.
         *
         * <p>Spent through the factor the compile loop already uses, so an
         * estimated run and a run that came out too long take one road — and
         * the loop shrinks it again if eight percent was not enough.
         */
        public double budgetFactor() {
            return estimated ? CapacityEstimator.SAFE_BUDGET : 1.0;
        }
    }
}
