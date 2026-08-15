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
 * @param textWidthPt      the width a line is set at, {@code \textwidth}
 * @param baselineSkipPt   the distance between consecutive baselines
 * @param fixedCosts       what the furniture costs: a section heading, an
 *                         entry heading, the overhead of a bullet list
 */
public record CapacityModel(
        double pageTextHeightPt,
        double textWidthPt,
        double baselineSkipPt,
        Map<String, Double> fixedCosts) {

    /**
     * The name, the headline and the contact line at the top of the page.
     * Calibrated for the shape the renderer emits: one name and two centred
     * lines under it.
     */
    public static final String HEADER_BLOCK = "headerBlock";

    /** A section heading with its rule and the space around it. */
    public static final String SECTION_HEADER = "sectionHeader";

    /**
     * The two lines of an entry heading — title, organization, dates — where
     * it follows a section heading.
     */
    public static final String ENTRY_HEADER = "entryHeader";

    /**
     * The same heading where a bullet list came before it.
     *
     * <p>Nine points more, because the paragraph skip between two blocks
     * applies and the section heading's own spacing does not. A CV of four
     * jobs pays it three times; charging every entry the cheaper number is how
     * a page overflows by half a bullet for no visible reason (EK D.8.10).
     */
    public static final String ENTRY_HEADER_AFTER_LIST = "entryHeaderAfterList";

    /** What a bullet list costs before its first bullet. */
    public static final String ITEMIZE_OVERHEAD = "itemizeOverhead";

    /** One bullet, at one line. Longer bullets are measured, not assumed. */
    public static final String ITEM_LINE = "itemLine";

    public CapacityModel {
        fixedCosts = Map.copyOf(Objects.requireNonNull(fixedCosts, "fixedCosts"));
        if (pageTextHeightPt <= 0 || textWidthPt <= 0 || baselineSkipPt <= 0) {
            throw new IllegalArgumentException("A page has a height, a width and a baseline");
        }
    }

    public double fixedCost(String name) {
        Double cost = fixedCosts.get(name);
        if (cost == null) {
            throw new IllegalArgumentException("Nothing measured for " + name);
        }
        return cost;
    }

    /**
     * What a bullet list adds between two items.
     *
     * <p>Derived rather than measured separately: a one-line item was measured
     * in place, and one line of it is a baseline, so whatever is left is the
     * separation. Keeping it derived means the two can never disagree.
     */
    public double itemSpacingPt() {
        return fixedCost(ITEM_LINE) - baselineSkipPt;
    }

    /** What is left for content once the page's furniture is paid for. */
    public double freeBudgetPt(int pages, double structuralCostPt) {
        if (pages < 1) {
            throw new IllegalArgumentException("A CV has at least one page");
        }
        return pages * pageTextHeightPt - structuralCostPt;
    }
}
