package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.HexColor;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.util.Locale;

/**
 * What a generation was asked for (Bolum 14.4).
 *
 * <p>Only the three fields Stage 1 can honour. The stored options of Bolum
 * 14.4 carry more — formats, a cover letter language, whether to file it under
 * tracking — and they arrive with the generation record itself in Stage 2
 * (EK D.8.8).
 *
 * @param language the wording to render, already resolved: {@code auto} means
 *                 "follow the posting", and in general mode there is no
 *                 posting to follow
 */
public record GenerationOptions(
        int maxPages, String language, TemplateCustomization customization) {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GenerationOptions.class);

    public GenerationOptions {
        if (maxPages < 1 || maxPages > 10) {
            throw new IllegalArgumentException("maxPages is between 1 and 10, was " + maxPages);
        }
        language = language == null || language.isBlank() ? "en" : language;
    }

    /**
     * What the profile's own defaults ask for, with anything the request
     * overrode already applied by the caller.
     */
    public static GenerationOptions defaultsOf(Profile profile) {
        var defaults = profile.getPreferences().defaults();
        return new GenerationOptions(
                defaults.maxPages(),
                resolveLanguage(defaults.cvLanguage(), profile.getSourceLanguage()),
                // The preference has carried a templateId since Bolum 14.4 and
                // this ignored it, so every CV came out classic whatever the
                // profile asked for. Reading it is the whole of what makes a
                // second template selectable (Bolum 33.5).
                customizationFor(defaults));
    }

    public GenerationOptions withMaxPages(Integer pages) {
        return pages == null ? this : new GenerationOptions(pages, language, customization);
    }

    /**
     * The defaults for a CV written against a posting.
     *
     * <p>The one thing that differs from {@link #defaultsOf}: {@code auto}
     * means "follow the posting", and here there is a posting to follow. In
     * general mode the same preference falls back to the profile's own source
     * language, because there is nothing else to read it from.
     *
     * <p><strong>And only when the profile can be written in it</strong>
     * (F-013). Bolum 21.8 fills a missing wording by translating it and saving
     * the result; that phase does not exist yet, so selection silently falls
     * back to the primary wording while the dates and "Present" keep following
     * the language that was asked for — a CV of Turkish bullets under English
     * dates. One document is written in one language, and which one is decided
     * here, from what the profile actually holds. When the translating phase
     * lands, {@link ProfileTree#canBeWrittenIn} is true for every language and
     * this narrows back to "follow the posting".
     *
     * @param tree            the profile as it will be selected from, which is
     *                        the only thing that knows whether a language is
     *                        deliverable
     * @param postingLanguage {@code jdLanguage} from Faz A, which may be blank
     *                        when the extraction did not name one
     */
    public static GenerationOptions forPosting(
            Profile profile, ProfileTree tree, String postingLanguage) {

        var defaults = profile.getPreferences().defaults();
        if (!"auto".equals(defaults.cvLanguage())
                || postingLanguage == null || postingLanguage.isBlank()) {
            return defaultsOf(profile);
        }
        String posting = postingLanguage.strip();
        if (tree == null || !tree.canBeWrittenIn(posting)) {
            return defaultsOf(profile);
        }
        return new GenerationOptions(defaults.maxPages(), posting,
                customizationFor(defaults));
    }

    public GenerationOptions withLanguage(String requested) {
        return requested == null || requested.isBlank()
                ? this
                : new GenerationOptions(maxPages, requested, customization);
    }

    public Locale locale() {
        return Locale.forLanguageTag(language);
    }

    private static String resolveLanguage(String preferred, String sourceLanguage) {
        return preferred == null || "auto".equals(preferred) ? sourceLanguage : preferred;
    }

    /**
     * The template the profile named, with whatever sliders it moved
     * (Bolum 33.1).
     *
     * <p>Null means "the template's own", field by field, so a person who
     * moved one slider carries one number and a template whose defaults change
     * later brings the rest along.
     *
     * <p><strong>An unusable stored value falls back rather than failing.</strong>
     * The ranges live on {@link TemplateCustomization} and a preference that
     * fell outside them -- written before a range moved, or by a release that
     * validated differently -- would otherwise throw here and take the CV with
     * it. A document in the template's own settings is a worse answer than the
     * one asked for and a much better one than no document.
     */
    private static TemplateCustomization customizationFor(Preferences.Defaults defaults) {
        TemplateCustomization base = TemplateRegistry.defaultsFor(defaults.templateId());
        Preferences.Appearance moved = defaults.appearance();
        if (moved == null || moved.isEmpty()) {
            return base;
        }
        try {
            return new TemplateCustomization(
                    base.baseTemplateId(),
                    moved.fontFamily() == null
                            ? base.fontFamily()
                            : FontFamily.valueOf(moved.fontFamily().toUpperCase(Locale.ROOT)),
                    moved.fontSizePt() == null ? base.fontSizePt() : moved.fontSizePt(),
                    moved.marginInches() == null ? base.marginInches() : moved.marginInches(),
                    moved.lineSpacing() == null ? base.lineSpacing() : moved.lineSpacing(),
                    moved.accentColor() == null
                            ? base.accentColor()
                            : HexColor.of(moved.accentColor()));
        } catch (IllegalArgumentException unusable) {
            // The value, never a name: there is nothing personal in a font
            // size, and the message is what says which knob was refused.
            log.warn("A stored appearance could not be used ({}); falling back to {}",
                    unusable.getMessage(), base.baseTemplateId());
            return base;
        }
    }
}
