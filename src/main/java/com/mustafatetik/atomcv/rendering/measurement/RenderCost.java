package com.mustafatetik.atomcv.rendering.measurement;

/**
 * What one piece of content costs on the page, as TeX measured it.
 *
 * @param heightPt how far the box rises above its baseline
 * @param depthPt  how far it hangs below — descenders, and the last line of a
 *                 multi-line paragraph
 */
public record RenderCost(double heightPt, double depthPt) {

    public RenderCost {
        if (heightPt < 0 || depthPt < 0) {
            throw new IllegalArgumentException("A box has no negative dimensions");
        }
    }

    /**
     * The vertical space the piece actually occupies (Bolum 26.2).
     *
     * <p>Height plus depth is the box; the baseline skip is the gap to
     * whatever follows it. Leaving that out is how a column of sixteen atoms
     * fits on paper in theory and overflows in practice.
     */
    public double totalPt(double baselineSkipPt) {
        return heightPt + depthPt + baselineSkipPt;
    }
}
