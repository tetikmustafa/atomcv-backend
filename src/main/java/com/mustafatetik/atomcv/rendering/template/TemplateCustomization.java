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

    public static final TemplateCustomization CLASSIC = new TemplateCustomization(
            "classic", FontFamily.SERIF, 10.0, 0.6, 1.0, HexColor.DEFAULT);

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
