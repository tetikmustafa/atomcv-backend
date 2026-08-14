package com.mustafatetik.atomcv.profile.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * How the user wants their CVs written and rendered, stored in
 * {@code profiles.preferences} (Bolum 14.3).
 *
 * @param writingStyle what the rewriting phase is told
 * @param defaults     what a generation assumes when the request says nothing
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Preferences(WritingStyle writingStyle, Defaults defaults) {

    public static final Preferences DEFAULTS =
            new Preferences(WritingStyle.DEFAULTS, Defaults.DEFAULTS);

    public Preferences {
        writingStyle = writingStyle == null ? WritingStyle.DEFAULTS : writingStyle;
        defaults = defaults == null ? Defaults.DEFAULTS : defaults;
    }

    /**
     * @param customInstructions free text the user writes for the rewriting
     *                           phase — user content, so it stays out of
     *                           {@code toString}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WritingStyle(
            boolean emphasizeMetrics,
            Tone tone,
            boolean conciseSentences,
            String customInstructions) {

        public static final WritingStyle DEFAULTS =
                new WritingStyle(true, Tone.FORMAL, false, null);

        @Override
        public String toString() {
            return "WritingStyle[tone=" + tone
                    + ", emphasizeMetrics=" + emphasizeMetrics
                    + ", conciseSentences=" + conciseSentences
                    + ", customInstructions=" + (customInstructions == null ? "none" : "set") + "]";
        }
    }

    /**
     * @param cvLanguage {@code auto} means "follow the posting", resolved
     *                   before selection because length differs by language
     *                   (Bolum 6.3)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Defaults(
            int maxPages,
            String templateId,
            String cvLanguage,
            String coverLetterLanguage) {

        public static final Defaults DEFAULTS = new Defaults(1, "classic", "auto", "auto");

        public Defaults {
            if (maxPages < 1) {
                throw new IllegalArgumentException("maxPages must be at least 1, was " + maxPages);
            }
            templateId = templateId == null ? "classic" : templateId;
            cvLanguage = cvLanguage == null ? "auto" : cvLanguage;
            coverLetterLanguage = coverLetterLanguage == null ? "auto" : coverLetterLanguage;
        }
    }
}
