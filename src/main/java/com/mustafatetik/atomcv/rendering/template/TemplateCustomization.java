package com.mustafatetik.atomcv.rendering.template;

import java.util.Objects;

/**
 * Everything a user may change about how a CV looks (Bolum 33.2).
 *
 * <p>The ranges are narrow on purpose: a bad-looking result should be
 * physically impossible rather than merely discouraged. The user is warned at
 * the edges — 9pt is legal and worth a word about ATS readability — but the
 * document cannot be made unreadable.
 *
 * <p>Everything here is either an enum, a validated value object or a number
 * inside a range. Nothing a user typed reaches LaTeX through this record, which
 * is what keeps Bolum 29's isolation a second line of defence rather than the
 * only one.
 */
public record TemplateCustomization(
        String baseTemplateId,
        FontFamily fontFamily,
        double fontSizePt,
        double marginInches,
        double lineSpacing,
        HexColor accentColor) {

    /**
     * The reference CV's own settings, to the point (Bolum 33.5).
     *
     * <p>Every one of these is a number read out of the document this template
     * was taken from rather than a taste: {@code \documentclass[11pt]},
     * Computer Modern because {@code article} sets no font package, and a
     * black section rule because it writes {@code \color{black}}.
     *
     * <p>The margin is the one that took arithmetic. The reference does not
     * load {@code geometry}: it loads {@code fullpage} and then moves the
     * margins by hand — {@code \oddsidemargin -0.5in}, {@code \textwidth +1in},
     * {@code \topmargin -.5in}, {@code \textheight +1.0in}. On letter paper
     * that lands on a text block of 7.5 by 10 inches, which is exactly half an
     * inch on all four sides. So {@code margin=0.5in} reproduces it exactly,
     * and the margin stays a slider the user can move (Bolum 33.1, layer B).
     */
    public static final TemplateCustomization CLASSIC = new TemplateCustomization(
            "classic", FontFamily.MODERN, 11.0, 0.5, 1.0, HexColor.of("000000"));

    public TemplateCustomization {
        Objects.requireNonNull(baseTemplateId, "baseTemplateId");
        fontFamily = fontFamily == null ? FontFamily.SERIF : fontFamily;
        accentColor = accentColor == null ? HexColor.DEFAULT : accentColor;

        requireInRange("fontSizePt", fontSizePt, 9.0, 12.0);
        requireInRange("marginInches", marginInches, 0.4, 1.0);
        requireInRange("lineSpacing", lineSpacing, 0.9, 1.3);
    }

    /**
     * The key measured render costs are stored under (Bolum 16.3).
     *
     * <p>It carries the template version, so a geometric change to the
     * renderer invalidates old measurements instead of silently keeping them —
     * which is how a page guarantee breaks without an error.
     */
    public String costKey() {
        return baseTemplateId + ":v" + TemplateRegistry.versionOf(baseTemplateId);
    }

    private static void requireInRange(String field, double value, double min, double max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    field + " must be between " + min + " and " + max + ", was " + value);
        }
    }
}
