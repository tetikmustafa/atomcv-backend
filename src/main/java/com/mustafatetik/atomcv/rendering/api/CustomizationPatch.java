package com.mustafatetik.atomcv.rendering.api;

import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A rename, a re-set, or both (Bolum 33.2).
 *
 * <p><strong>The settings are all-or-nothing.</strong> Sending
 * {@code baseTemplateId} replaces every parameter, with anything omitted
 * taking that template's default; sending none of them leaves the settings
 * alone and renames. Bolum 33.2's parameters are read together by the
 * renderer, so a request that changed one of five would be describing a page
 * nobody chose.
 */
@Schema(description = "What to change about a saved set")
public record CustomizationPatch(
        @Schema(description = "A new name, or absent to keep the one it has")
        @Size(max = 60)
        String name,

        @Schema(description = "Present to replace the settings; absent to leave them")
        @Size(max = 40)
        String baseTemplateId,

        @DecimalMin("9.0") @DecimalMax("12.0") Double fontSizePt,
        @DecimalMin("0.4") @DecimalMax("1.0") Double marginInches,
        @DecimalMin("0.9") @DecimalMax("1.3") Double lineSpacing,
        String fontFamily,
        @Pattern(regexp = "^[0-9A-Fa-f]{6}$") String accentColor) {

    /** Null when this patch is only a rename. */
    public TemplateCustomization toSettingsOrNull() {
        if (baseTemplateId == null || baseTemplateId.isBlank()) {
            return null;
        }
        return new CustomizationRequest(
                name == null || name.isBlank() ? "unused" : name,
                baseTemplateId, fontSizePt, marginInches, lineSpacing,
                fontFamily, accentColor).toSettings();
    }
}
