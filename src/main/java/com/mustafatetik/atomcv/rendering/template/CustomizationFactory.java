package com.mustafatetik.atomcv.rendering.template;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A template and the sliders somebody moved, as one customization
 * (Bolum 33.1).
 *
 * <p>Plain values rather than a preferences record, so the rendering module
 * does not learn what a profile is. Two callers need this and they are in
 * different modules: the generation that draws the CV, and the profile update
 * that has to notice a geometry nobody has measured.
 *
 * <p><strong>Null means "the template's own", field by field.</strong> That is
 * what keeps every preference written before the sliders existed correct, and
 * what lets somebody who moved one slider carry one number — a template whose
 * defaults change later brings the rest along.
 */
public final class CustomizationFactory {

    private static final Logger log = LoggerFactory.getLogger(CustomizationFactory.class);

    private CustomizationFactory() {
    }

    /**
     * <strong>An unusable value falls back to the template rather than
     * failing.</strong> The ranges live on {@link TemplateCustomization} and a
     * stored preference outside them — written before a range moved, or by a
     * release that validated differently — would otherwise throw wherever a CV
     * is being made. A document in the template's own settings is a worse
     * answer than the one asked for and a much better one than no document.
     */
    public static TemplateCustomization from(
            String templateId,
            Double fontSizePt,
            Double marginInches,
            Double lineSpacing,
            String fontFamily,
            String accentColor) {

        TemplateCustomization base = TemplateRegistry.defaultsFor(templateId);
        if (fontSizePt == null && marginInches == null && lineSpacing == null
                && fontFamily == null && accentColor == null) {
            return base;
        }
        try {
            return new TemplateCustomization(
                    base.baseTemplateId(),
                    fontFamily == null
                            ? base.fontFamily()
                            // Locale.ROOT: absolute rule 7. Under a Turkish
                            // locale "serif" upper-cases to "SERİF".
                            : FontFamily.valueOf(fontFamily.toUpperCase(Locale.ROOT)),
                    fontSizePt == null ? base.fontSizePt() : fontSizePt,
                    marginInches == null ? base.marginInches() : marginInches,
                    lineSpacing == null ? base.lineSpacing() : lineSpacing,
                    accentColor == null ? base.accentColor() : HexColor.of(accentColor));
        } catch (IllegalArgumentException unusable) {
            // The message names the knob and the number; there is nothing
            // personal in a font size (absolute rule 4).
            log.warn("A stored appearance could not be used ({}); falling back to {}",
                    unusable.getMessage(), base.baseTemplateId());
            return base;
        }
    }
}
