package com.mustafatetik.atomcv.rendering.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * What a measured render cost is filed under (Bolum 16.3, 33.1).
 *
 * <p>The key decides which stored numbers a document is allowed to reuse, so
 * every case here is a question about money or about the page guarantee: too
 * loose and a CV is charged for boxes it does not have, too tight and a person
 * pays for a compilation to learn what was already known.
 */
class TemplateCustomizationCostKeyTest {

    /**
     * Every measurement in the database and in the golden set is under this,
     * and they were taken at exactly these settings. Suffixing them would
     * orphan work that is still correct.
     */
    @Test
    void atemplateAtItsOwnDefaultsKeepsTheBareKey() {
        assertThat(TemplateCustomization.CLASSIC.costKey()).isEqualTo("classic:v5");
        assertThat(TemplateCustomization.COMPACT.costKey()).isEqualTo("compact:v1");
    }

    /**
     * <strong>The trap layer B was walking into.</strong> This key was
     * {@code templateId:vN} and nothing else, so a CV at 9pt would have read
     * the bullet costs measured at 11pt out of the same entry — a stored cost
     * describing a different document, which is the quiet failure the whole
     * measurement subsystem exists to prevent.
     */
    @Test
    void amovedKnobIsAdifferentDocumentAndAdifferentKey() {
        var smaller = withFontSize(9.0);

        assertThat(smaller.costKey()).isNotEqualTo(TemplateCustomization.CLASSIC.costKey());
        assertThat(smaller.costKey()).isEqualTo("classic:v5:modern-9.0-0.50-1.00");
    }

    @Test
    void eachGeometricKnobMovesTheKeyOnItsOwn() {
        assertThat(withFontSize(10.0).costKey())
                .isNotEqualTo(TemplateCustomization.CLASSIC.costKey());
        assertThat(withMargin(0.75).costKey())
                .isNotEqualTo(TemplateCustomization.CLASSIC.costKey());
        assertThat(withSpacing(1.15).costKey())
                .isNotEqualTo(TemplateCustomization.CLASSIC.costKey());
        assertThat(withFont(FontFamily.SERIF).costKey())
                .isNotEqualTo(TemplateCustomization.CLASSIC.costKey());
    }

    /**
     * <strong>Colour is layer A: "no re-measurement" (Bolum 33.1).</strong>
     *
     * <p>It moves no box on the page. A key that included the accent would
     * throw away every measurement a person owns the first time they changed a
     * heading from black, and charge them a compilation to learn the same
     * numbers again — a bill for a free change.
     */
    @Test
    void colourDoesNotChangeTheKey() {
        var blue = new TemplateCustomization("classic", FontFamily.MODERN, 11.0, 0.5, 1.0,
                HexColor.of("1D4ED8"));

        assertThat(blue.costKey()).isEqualTo(TemplateCustomization.CLASSIC.costKey());
    }

    /** And two documents of the same geometry share their measurements. */
    @Test
    void twoColoursOfOneGeometryShareOneKey() {
        var red = new TemplateCustomization("classic", FontFamily.MODERN, 9.5, 0.5, 1.0,
                HexColor.of("B91C1C"));
        var green = new TemplateCustomization("classic", FontFamily.MODERN, 9.5, 0.5, 1.0,
                HexColor.of("15803D"));

        assertThat(red.costKey()).isEqualTo(green.costKey());
    }

    /**
     * The same knobs under two templates are two documents: the preamble is
     * the difference the suffix cannot see.
     */
    @Test
    void thesameKnobsUnderTwoTemplatesAreTwoKeys() {
        var classicAtCompactSettings =
                new TemplateCustomization("classic", FontFamily.MODERN, 10.0, 0.4, 0.95,
                        HexColor.of("000000"));

        assertThat(classicAtCompactSettings.costKey())
                .isNotEqualTo(TemplateCustomization.COMPACT.costKey())
                .isEqualTo("classic:v5:modern-10.0-0.40-0.95");
    }

    /**
     * Written out rather than compared to itself: this string lands in a JSONB
     * column and in a golden fixture, and a format that drifted would orphan
     * every measurement filed under the old one without failing anywhere.
     * Absolute rule 7 is in it too — under a Turkish locale an unguarded
     * {@code %.1f} writes {@code 9,5}.
     */
    @Test
    void thesuffixIsAstableReadableShape() {
        var moved = new TemplateCustomization("classic", FontFamily.SANS, 9.5, 0.65, 1.25,
                HexColor.of("000000"));

        assertThat(moved.costKey()).isEqualTo("classic:v5:sans-9.5-0.65-1.25");
    }

    private static TemplateCustomization withFontSize(double points) {
        return new TemplateCustomization("classic", FontFamily.MODERN, points, 0.5, 1.0,
                HexColor.of("000000"));
    }

    private static TemplateCustomization withMargin(double inches) {
        return new TemplateCustomization("classic", FontFamily.MODERN, 11.0, inches, 1.0,
                HexColor.of("000000"));
    }

    private static TemplateCustomization withSpacing(double spacing) {
        return new TemplateCustomization("classic", FontFamily.MODERN, 11.0, 0.5, spacing,
                HexColor.of("000000"));
    }

    private static TemplateCustomization withFont(FontFamily family) {
        return new TemplateCustomization("classic", family, 11.0, 0.5, 1.0,
                HexColor.of("000000"));
    }
}
