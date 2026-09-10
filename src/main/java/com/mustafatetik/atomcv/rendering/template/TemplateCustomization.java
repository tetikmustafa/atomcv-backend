package com.mustafatetik.atomcv.rendering.template;

import java.util.Locale;
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

    /**
     * Compact at its own settings (Bolum 33.5).
     *
     * <p>Three of the four differences from classic are layer B — 10pt rather
     * than 11, a 0.4in margin rather than 0.5, 0.95 leading rather than 1.0 —
     * and the fourth is the preamble the id names. They are separated on
     * purpose: a person who wants classic's furniture at compact's density can
     * have it by moving the sliders, and a person who wants compact can start
     * here rather than discovering the combination.
     *
     * <p>0.4in is the floor {@link #requireInRange} allows, and it is a floor
     * because an ATS that crops a margin loses a line rather than a space.
     */
    public static final TemplateCustomization COMPACT = new TemplateCustomization(
            "compact", FontFamily.MODERN, 10.0, 0.4, 0.95, HexColor.of("000000"));

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
     *
     * <p><strong>And it carries the geometry, which it did not.</strong> Until
     * layer B this was {@code templateId:vN} and nothing else, so a CV at 9pt
     * would have read the bullet costs measured at 11pt out of the same entry.
     * Nothing could reach that yet — {@link TemplateRegistry#capacityOf}
     * refuses a customization nobody has measured, so such a generation never
     * ran — but it is the trap waiting for the sliders, and a stored cost that
     * describes a different document is the quiet failure this whole subsystem
     * exists to prevent.
     *
     * <p><strong>Colour is not in it, on purpose.</strong> Bolum 33.1 puts
     * colours in layer A — "no re-measurement" — because they move no box on
     * the page. A key that included the accent would throw away every
     * measurement a person owns the first time they changed a heading from
     * black, and charge them a compilation to learn the same numbers again.
     *
     * <p><strong>A template at its own defaults keeps the bare key.</strong>
     * {@code classic:v4} is what every measurement in the database and in the
     * golden set is filed under, and those were taken at exactly those
     * settings — suffixing them would orphan work that is still correct. The
     * suffix is for a document that differs from the default, which is the
     * only case that needs telling apart.
     */
    public String costKey() {
        String base = baseTemplateId + ":v" + TemplateRegistry.versionOf(baseTemplateId);
        // Compared on the geometry rather than on the whole record, and the
        // first draft did the latter: an accent that was not black made the
        // customization unequal to the default and earned a suffix, which is
        // the layer-A promise broken in the direction that costs a person a
        // compilation. The test named the colour case for that reason.
        String geometry = geometry();
        return geometry.equals(TemplateRegistry.defaultsFor(baseTemplateId).geometry())
                ? base
                : base + ":" + geometry;
    }

    /**
     * The four knobs that move a box, as a readable, stable suffix.
     *
     * <p>Readable rather than a digest because it lands in a JSONB column and
     * in a golden fixture, and a person reading either should be able to tell
     * what a row describes without running anything. {@code Locale.ROOT} on
     * both the numbers and the enum: absolute rule 7, and under a Turkish
     * locale this would otherwise write {@code 9,5} and turn {@code SANS} into
     * {@code sans} through a different path than every other reader uses.
     */
    private String geometry() {
        return String.format(Locale.ROOT, "%s-%.1f-%.2f-%.2f",
                fontFamily.name().toLowerCase(Locale.ROOT),
                fontSizePt, marginInches, lineSpacing);
    }

    private static void requireInRange(String field, double value, double min, double max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    field + " must be between " + min + " and " + max + ", was " + value);
        }
    }
}
