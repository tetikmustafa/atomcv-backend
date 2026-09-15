package com.mustafatetik.atomcv.rendering.api;

import com.mustafatetik.atomcv.rendering.domain.SavedCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One saved set, as a client reads it back.
 *
 * <p>Flat rather than a nested {@code params} object: the column is nested
 * because JSONB has to be, and the wire shape is the shape the request has —
 * a client that reads a set and writes it straight back should not have to
 * unwrap it (the lesson of {@code Appearance} in F-033).
 *
 * @param templateVersion the renderer version this was saved against
 * . A client caching anything derived from
 *                        it keys on this; a mismatch with
 *                        {@code GET /templates} means the geometry moved under
 *                        it and any measured cost is void
 */
@Schema(description = "A saved set of appearance settings")
public record CustomizationResponse(
        UUID id,
        String name,
        String baseTemplateId,
        int templateVersion,
        double fontSizePt,
        double marginInches,
        double lineSpacing,
        String fontFamily,
        String accentColor,
        Instant createdAt) {

    public static CustomizationResponse of(SavedCustomization saved) {
        TemplateCustomization settings = saved.settings();
        return new CustomizationResponse(
                saved.getId(),
                saved.getName(),
                settings.baseTemplateId(),
                saved.getTemplateVersion(),
                settings.fontSizePt(),
                settings.marginInches(),
                settings.lineSpacing(),
                settings.fontFamily().wireValue(),
                settings.accentColor().value(),
                saved.getCreatedAt());
    }
}
