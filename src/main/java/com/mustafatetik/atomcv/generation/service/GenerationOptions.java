package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.rendering.template.CustomizationFactory;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
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
     * <p><strong>It follows the posting even when the profile holds no wording
     * in that language</strong>, which is the narrowing F-013 itself asked for
     * once Bolum 21.8's second step existed. This gated on
     * {@link ProfileTree#canBeWrittenIn} while that step did not: selection
     * would fall back to the primary wording for every untranslated atom while
     * the dates and "Present" kept following the language that was asked for,
     * a CV of Turkish bullets under English dates.
     *
     * <p><strong>The decision moved rather than went away.</strong>
     * {@code GenerationTranslation} fills the missing wordings between Faz B
     * and Faz C and reports when it could not, and that is the one place a
     * document's language falls back now. One document is still written in one
     * language — a static read of the tree is simply no longer what settles
     * which, because a tree that cannot be written in a language at nine in
     * the morning can be at five past.
     *
     * @param postingLanguage {@code jdLanguage} from Faz A, which may be blank
     *                        when the extraction did not name one
     */
    public static GenerationOptions forPosting(Profile profile, String postingLanguage) {
        var defaults = profile.getPreferences().defaults();
        if (!"auto".equals(defaults.cvLanguage())
                || postingLanguage == null || postingLanguage.isBlank()) {
            return defaultsOf(profile);
        }
        return new GenerationOptions(defaults.maxPages(), postingLanguage.strip(),
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
     * The same options, rendered with a set somebody saved (Bolum 14.4's
     * {@code options.customizationId}).
     *
     * <p>Null leaves the profile's working settings alone, which is what a
     * request that names nothing means — and that is nearly every request.
     * A named set wins because naming one is the more specific statement.
     */
    public GenerationOptions withCustomization(TemplateCustomization named) {
        return named == null ? this : new GenerationOptions(maxPages, language, named);
    }

    /** The template the profile named, with whatever sliders it moved. */
    private static TemplateCustomization customizationFor(Preferences.Defaults defaults) {
        Preferences.Appearance moved = defaults.appearance();
        return CustomizationFactory.from(
                defaults.templateId(),
                moved == null ? null : moved.fontSizePt(),
                moved == null ? null : moved.marginInches(),
                moved == null ? null : moved.lineSpacing(),
                moved == null ? null : moved.fontFamily(),
                moved == null ? null : moved.accentColor());
    }
}
