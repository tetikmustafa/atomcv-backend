package com.mustafatetik.atomcv.rendering.api;

import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.HexColor;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A set of appearance settings to keep under a name.
 *
 * <p><strong>the ranges, and they are the safety.</strong> Font size 9 to 12,
 * margin 0.4 to 1.0 inches, line spacing 0.9 to 1.3. The section's own
 * sentence: the ranges are kept narrow so that a bad result is physically
 * impossible rather than merely discouraged. A value outside one is refused
 * here, before it can reach a preamble.

 * <p>Everything but the name and the template is optional; an omitted value
 * takes the base template's own default, which is what somebody who moved one
 * slider means.
 */
@Schema(description = "Appearance settings to keep")
public record CustomizationRequest(
        @Schema(description = "What to call it; unique within the profile",
                example = "Compact, one page")
        @NotBlank @Size(max = 60)
        String name,

        @Schema(description = "Which template it is built on", example = "classic")
        @NotBlank @Size(max = 40)
        String baseTemplateId,

        @Schema(description = "9 to 12", example = "10.5")
        @DecimalMin("9.0") @DecimalMax("12.0")
        Double fontSizePt,

        @Schema(description = "0.4 to 1.0 inches", example = "0.5")
        @DecimalMin("0.4") @DecimalMax("1.0")
        Double marginInches,

        @Schema(description = "0.9 to 1.3", example = "1.0")
        @DecimalMin("0.9") @DecimalMax("1.3")
        Double lineSpacing,

        @Schema(description = "One of the whitelisted families", example = "sans")
        String fontFamily,

        @Schema(description = "Six hex digits, no hash", example = "1D4ED8")
        @Pattern(regexp = "^[0-9A-Fa-f]{6}$")
        String accentColor) {

    /**
     * @throws IllegalArgumentException for a font family or template the
     *         registry does not know. {@code ProblemDetailAdvice} turns that
     *         into a 400 naming the field, which is what a request-body
     *  violation is
     */
    public TemplateCustomization toSettings() {
        TemplateCustomization base = TemplateRegistry.ids().contains(baseTemplateId)
                ? TemplateRegistry.defaultsFor(baseTemplateId)
                : TemplateCustomization.CLASSIC;

        return new TemplateCustomization(
                baseTemplateId,
                fontFamily == null ? base.fontFamily() : FontFamily.fromWireValue(fontFamily),
                fontSizePt == null ? base.fontSizePt() : fontSizePt,
                marginInches == null ? base.marginInches() : marginInches,
                lineSpacing == null ? base.lineSpacing() : lineSpacing,
                accentColor == null ? base.accentColor() : HexColor.of(accentColor));
    }
}
