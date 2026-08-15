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
     * The vertical space the piece occupies inside a bullet list (Bolum 26.2).
     *
     * <p>A box of n lines advances the page by n baselines and the list's own
     * item separation — not by the box's own height plus a baseline. The
     * difference is about eight points per bullet, which on a full page of
     * twenty bullets is a third of the page left blank for no reason
     * (EK D.8.10).
     *
     * <p>Rounding to whole lines here is not the rounding Bolum 26.3 warns
     * against. That warning is about turning a measurement into lines and
     * losing the remainder; this is TeX's own arithmetic — consecutive
     * baselines are exactly {@code \baselineskip} apart, so the height of n
     * lines is exactly n baselines. The sum stays in points.
     *
     * @param baselineSkipPt the distance between consecutive baselines
     * @param itemSpacingPt  what the list adds between two items, which is the
     *                       measured cost of a one-line item less one baseline
     */
    public double totalPt(double baselineSkipPt, double itemSpacingPt) {
        return lines(baselineSkipPt) * baselineSkipPt + itemSpacingPt;
    }

    /**
     * How many lines the content wrapped onto.
     *
     * <p>A box of n lines measures {@code (n-1)} baselines plus one line's own
     * ascent and descent, and that last part is always less than a baseline
     * for a font at a sane size — so dividing and rounding up gives n back
     * exactly rather than approximately.
     */
    public int lines(double baselineSkipPt) {
        return Math.max(1, (int) Math.ceil((heightPt + depthPt) / baselineSkipPt));
    }
}
