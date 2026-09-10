package com.mustafatetik.atomcv.rendering.measurement;

import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A capacity for a geometry nobody has compiled yet (Bolum 33.3).
 *
 * <p>Bolum 33.3 does not make a person wait for a compilation: a slider moves,
 * a measurement is queued, and a generation asked for before it lands runs on
 * an estimate with a margin. This is that estimate.
 *
 * <p><strong>It is scaled from the template's own measured default, and the
 * direction of the error is the whole design.</strong> A capacity that is too
 * generous over-fills the page, which is the one failure this subsystem exists
 * to prevent; a capacity that is too mean prints a CV with room to spare,
 * which nobody notices. So every term is scaled by the font and the leading —
 * furniture included, even though negative spacing is absolute points and does
 * not really scale — and the caller then spends only {@link #SAFE_BUDGET} of
 * the page.
 *
 * <p><strong>What it is not.</strong> It is not a measurement and must not be
 * stored as one: {@code template_capacities} holds what a compiler said, and a
 * scaled guess written there would be indistinguishable from one afterwards.
 * The estimate lives for one generation.
 */
public final class CapacityEstimator {

    /**
     * The share of the page an estimated run allows itself (Bolum 33.3's
     * "estimate plus 8%").
     *
     * <p>Spent through the budget factor the compile loop already uses rather
     * than by shaving the page height here, so an estimated run and a run that
     * came out too long take the same road — and the loop shrinks it again if
     * eight percent was not enough.
     *
     * <p>{@code CapacityEstimatorIT} measures a real non-default geometry and
     * checks the estimate lands inside this margin. Without that the number
     * would be one copied out of a specification.
     */
    public static final double SAFE_BUDGET = 0.92;

    private CapacityEstimator() {
    }

    /**
     * @return a scaled capacity, or empty when the template itself has no
     *         measured default to scale from — which would mean estimating
     *         from nothing
     */
    public static Optional<CapacityModel> estimate(TemplateCustomization customization) {
        TemplateCustomization defaults =
                TemplateRegistry.defaultsFor(customization.baseTemplateId());
        Optional<CapacityModel> known = TemplateRegistry.capacityOf(defaults);
        if (known.isEmpty()) {
            return Optional.empty();
        }
        CapacityModel base = known.get();

        // Vertical scale: a line is taller at a bigger font and at looser
        // leading, and everything set on the page follows it. Measured against
        // a real 12pt page this over-predicts by a few percent -- which is the
        // safe direction, because a taller line is charged more points.
        double vertical = (customization.fontSizePt() / defaults.fontSizePt())
                * (customization.lineSpacing() / defaults.lineSpacing());

        var fixed = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> entry : base.fixedCosts().entrySet()) {
            fixed.put(entry.getKey(), entry.getValue() * vertical);
        }

        return Optional.of(new CapacityModel(
                // The text block is geometry, and the margin is the only knob
                // that moves it: letter paper less twice the margin, in points.
                // Not exact -- `geometry` rounds on its way to a text block, by
                // about a tenth of a point -- and a tenth of a point against
                // the margin below it is not worth a second approximation.
                base.pageTextHeightPt() + inches(defaults.marginInches() - customization.marginInches()),
                base.textWidthPt() + inches(defaults.marginInches() - customization.marginInches()),
                base.baselineSkipPt() * vertical,
                base.itemBaselineSkipPt() * vertical,
                fixed));
    }

    /** Two edges, seventy-two points to the inch. */
    private static double inches(double difference) {
        return difference * 2 * 72.0;
    }
}
