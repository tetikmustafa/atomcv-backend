package com.mustafatetik.atomcv.profile.api.dto;

import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.profile.domain.Tone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Replacement preferences (Bolum 14.3).
 *
 * <p>Separate from the domain record because the domain one enforces its
 * invariants in a constructor, and a constructor that throws during
 * deserialization produces a failure the client cannot read. Bounds belong
 * where the client can be told about them.
 */
@Schema(name = "PreferencesUpdate")
public record PreferencesUpdateRequest(
        @Valid WritingStyleRequest writingStyle,
        @Valid DefaultsRequest defaults) {

    @Schema(name = "WritingStyleUpdate")
    public record WritingStyleRequest(
            boolean emphasizeMetrics,
            Tone tone,
            boolean conciseSentences,
            @Size(max = 1000) String customInstructions) {
    }

    @Schema(name = "DefaultsUpdate")
    public record DefaultsRequest(
            @Min(1) @Max(10) int maxPages,
            @Size(max = 40) String templateId,
            @Size(min = 2, max = 16) String cvLanguage,
            @Size(min = 2, max = 16) String coverLetterLanguage) {
    }

    public Preferences toPreferences() {
        return new Preferences(
                writingStyle == null ? null : new Preferences.WritingStyle(
                        writingStyle.emphasizeMetrics(),
                        writingStyle.tone() == null ? Tone.FORMAL : writingStyle.tone(),
                        writingStyle.conciseSentences(),
                        writingStyle.customInstructions()),
                defaults == null ? null : new Preferences.Defaults(
                        defaults.maxPages(),
                        defaults.templateId(),
                        defaults.cvLanguage(),
                        defaults.coverLetterLanguage()));
    }
}
