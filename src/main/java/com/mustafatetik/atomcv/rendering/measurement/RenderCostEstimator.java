package com.mustafatetik.atomcv.rendering.measurement;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;

/**
 * What a piece of content probably costs, when nobody has measured it
 * (Bolum 26.5).
 *
 * <p>Selection needs a number for every atom. A measurement is one compilation
 * away and normally already stored, but a wording written a second ago has
 * none — and an atom with no number cannot simply be dropped, because dropping
 * it is exactly the silent bad result the product refuses to produce.
 *
 * <p><strong>It errs upwards, always.</strong> An overestimate costs the user
 * a bullet that would have fitted; an underestimate costs a page, which is the
 * one promise the product makes. Every constant here is chosen with that
 * asymmetry in mind, and a test against the real compiler asserts the estimate
 * is never below what TeX charges.
 *
 * <p>Bolum 26.2 builds this on real font metrics read out of the TTF with
 * FontBox. That is a better estimator and it needs a PDF library; this one
 * needs nothing, and where the two differ this one is the more pessimistic
 * (EK D.8.7).
 */
public final class RenderCostEstimator {

    /**
     * Bolum 26.5: an unmeasured atom carries a safety margin. Kept here rather
     * than at the call site so that "the estimate" always means the padded one.
     */
    static final double SAFETY_MARGIN = 1.08;

    /**
     * Average advance width as a share of the font size, for a Latin serif at
     * mixed case. Deliberately below the real average for Termes (~0.52em):
     * a narrower character means more of them per line, more lines, and a
     * larger estimate.
     */
    static final double AVERAGE_ADVANCE_EM = 0.46;

    /** Bolum 26.2: TeX fills about 92% of a line before breaking it. */
    static final double LINE_FILL = 0.92;

    private RenderCostEstimator() {
    }

    /**
     * @param widthPt the width the content is set at — a bullet is indented,
     *                so this is narrower than the page's own text width
     */
    public static double estimatePt(
            RichContent content,
            TemplateCustomization customization,
            CapacityModel capacity,
            double widthPt) {

        double charactersPerLine =
                widthPt / (customization.fontSizePt() * AVERAGE_ADVANCE_EM) * LINE_FILL;
        int characters = content.plainText().length();
        int lines = Math.max(1, (int) Math.ceil(characters / charactersPerLine));

        // The same shape a measurement has: n baselines and the list's own
        // separation (see RenderCost), with a line's worth of headroom on top
        // because a guessed line count can be one short.
        return ((lines + 1) * capacity.baselineSkipPt() + capacity.itemSpacingPt())
                * SAFETY_MARGIN;
    }

    /** The same, at the width a bullet actually gets. */
    public static double estimateBulletPt(
            RichContent content, TemplateCustomization customization, CapacityModel capacity) {

        return estimatePt(content, customization, capacity,
                capacity.textWidthPt() - bulletIndentPt(customization));
    }

    /**
     * The classic template's {@code leftmargin=0.15in} plus the label. Points
     * per inch is TeX's 72.27, not PostScript's 72.
     */
    private static double bulletIndentPt(TemplateCustomization customization) {
        return 0.15 * 72.27 + customization.fontSizePt();
    }
}
