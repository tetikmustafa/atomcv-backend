package com.mustafatetik.atomcv.profile.api.dto;

import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.profile.domain.Tone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
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
            @Size(min = 2, max = 16) String coverLetterLanguage,
            @Valid AppearanceRequest appearance) {
    }

    /**
     * Bolum 33.1's layer A and B, as five nullable knobs.
     *
     * <p>Null means "leave the template's own", field by field. Somebody who
     * moves one slider sends one number, and a template whose defaults change
     * later carries the rest along.
     *
     * <p>The bounds repeat {@code TemplateCustomization}'s, and repeating them
     * is the point: the record throws on a value outside them, and a throw at
     * generation time is a CV that does not get made. Here it is a 400 on the
     * request that asked for it, while the person is still looking at the
     * slider.
     *
     * <p>The ranges are narrow because Bolum 33.2 wants a bad-looking result
     * to be physically impossible. 9pt is legal and worth a word about ATS
     * readability; it is not refused.
     */
    @Schema(name = "AppearanceUpdate", description = """
            How the CV looks. Every field is optional and omitting one leaves             the template's own setting.

            The four geometric ones cost a measurement — a CV asked for             before it lands is produced against an estimate that spends a             little less of the page, so the page limit still holds. The             colour costs nothing.""")
    public record AppearanceRequest(
            @DecimalMin("9.0") @DecimalMax("12.0") Double fontSizePt,
            @DecimalMin("0.4") @DecimalMax("1.0") Double marginInches,
            @DecimalMin("0.9") @DecimalMax("1.3") Double lineSpacing,
            @Pattern(regexp = "MODERN|SERIF|SANS") String fontFamily,
            @Pattern(regexp = "^[0-9A-Fa-f]{6}$") String accentColor) {
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
                        defaults.coverLetterLanguage(),
                        defaults.appearance() == null ? null : new Preferences.Appearance(
                                defaults.appearance().fontSizePt(),
                                defaults.appearance().marginInches(),
                                defaults.appearance().lineSpacing(),
                                defaults.appearance().fontFamily(),
                                defaults.appearance().accentColor())));
    }
}
