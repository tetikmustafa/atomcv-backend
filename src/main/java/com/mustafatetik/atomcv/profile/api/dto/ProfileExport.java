package com.mustafatetik.atomcv.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * The whole profile in one document (Bolum 13.1: leaving has to be possible).
 *
 * <p>Nested rather than flat, unlike the editing endpoints: an export is read
 * by a person or fed back in whole, and both want the structure visible. The
 * item shapes are the published ones, so what comes out of an export is what
 * the API already describes.
 */
@Schema(name = "ProfileExport", description = "A complete, self-contained copy of a profile")
public record ProfileExport(
        @Schema(description = "When this copy was made") Instant exportedAt,
        ProfileResponse profile,
        List<SectionExport> sections) {

    @Schema(name = "SectionExport")
    public record SectionExport(
            SectionResponse section,
            List<EntryExport> entries,
            @Schema(description = "Atoms hanging straight off the section")
            List<AtomResponse> atoms) {
    }

    @Schema(name = "EntryExport")
    public record EntryExport(EntryResponse entry, List<AtomResponse> atoms) {
    }
}
