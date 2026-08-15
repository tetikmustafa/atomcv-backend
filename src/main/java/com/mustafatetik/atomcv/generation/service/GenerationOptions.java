package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.profile.domain.Profile;
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
                TemplateCustomization.CLASSIC);
    }

    public GenerationOptions withMaxPages(Integer pages) {
        return pages == null ? this : new GenerationOptions(pages, language, customization);
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
}
