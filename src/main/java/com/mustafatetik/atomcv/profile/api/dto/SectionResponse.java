package com.mustafatetik.atomcv.profile.api.dto;

import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A section as the API publishes it.
 *
 * <p>The version is a field here, unlike on the profile head. Sections appear
 * both alone and inside a collection, and a field that vanished depending on
 * which endpoint returned it would be worse than the small redundancy of
 * sending it beside the {@code ETag} (EK D.6.2).
 */
@Schema(name = "Section")
public record SectionResponse(
        UUID id,
        SectionKind kind,
        @Schema(description = "The heading as rendered", example = "Experience") String title,
        SectionLayout layout,
        @Schema(description = "Position among the sections, from 0") short displayOrder,
        @Schema(description = "Selection may not drop it, whatever the budget says")
        boolean alwaysInclude,
        @Schema(description = "Rendered as written; no rewriting touches it") boolean verbatim,
        @Schema(description = "Inactive sections enter no CV at all") boolean active,
        @Schema(description = "Send it back as If-Match on the next write", example = "0")
        long version) {

    public static SectionResponse of(Section section) {
        return new SectionResponse(
                section.getId(),
                section.getKind(),
                section.getTitle(),
                section.getLayout(),
                section.getDisplayOrder(),
                section.isAlwaysInclude(),
                section.isVerbatim(),
                section.isActive(),
                section.getVersion() == null ? 0L : section.getVersion());
    }
}
