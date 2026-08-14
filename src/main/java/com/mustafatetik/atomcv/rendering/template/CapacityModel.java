package com.mustafatetik.atomcv.rendering.template;

import java.util.Map;
import java.util.Objects;

/**
 * What a page of this template holds, in points (Bolum 26.3, 26.4).
 *
 * <p>Points, never lines. Rounding each atom up to a whole line accumulates
 * error — sixteen atoms can drift by sixteen lines — so everything is summed
 * in points and compared with the capacity once, at the end.
 *
 * <p>Every number here was measured against the real compiler, not estimated.
 * A calibration test re-derives them and fails when the template moves, which
 * is the moment its version has to be raised (Bolum 16.3).
 *
 * @param pageTextHeightPt the height of the text block, {@code \textheight}
 * @param baselineSkipPt   the distance between consecutive baselines
 * @param fixedCosts       what the furniture costs: a section heading, an
 *                         entry heading, the overhead of a bullet list
 */
public record CapacityModel(
        double pageTextHeightPt,
        double baselineSkipPt,
        Map<String, Double> fixedCosts) {

    /** A section heading with its rule and the space around it. */
    public static final String SECTION_HEADER = "sectionHeader";

    /** The two lines of an entry heading: title, organization, dates. */
    public static final String ENTRY_HEADER = "entryHeader";

    /** What a bullet list costs before its first bullet. */
    public static final String ITEMIZE_OVERHEAD = "itemizeOverhead";

    /** One bullet, at one line. Longer bullets are measured, not assumed. */
    public static final String ITEM_LINE = "itemLine";

    public CapacityModel {
        fixedCosts = Map.copyOf(Objects.requireNonNull(fixedCosts, "fixedCosts"));
        if (pageTextHeightPt <= 0 || baselineSkipPt <= 0) {
            throw new IllegalArgumentException("A page has a height and a baseline");
        }
    }

    public double fixedCost(String name) {
        Double cost = fixedCosts.get(name);
        if (cost == null) {
            throw new IllegalArgumentException("Nothing measured for " + name);
        }
        return cost;
    }

    /** What is left for content once the page's furniture is paid for. */
    public double freeBudgetPt(int pages, double structuralCostPt) {
        if (pages < 1) {
            throw new IllegalArgumentException("A CV has at least one page");
        }
        return pages * pageTextHeightPt - structuralCostPt;
    }
}
