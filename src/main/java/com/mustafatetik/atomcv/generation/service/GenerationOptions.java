package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
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
                TemplateCustomization.CLASSIC);
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
