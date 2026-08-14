package com.mustafatetik.atomcv.profile.api.dto;

import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * A partial update: a field left out is left alone (Bolum 35.6).
 *
 * <p>Every column behind these is {@code NOT NULL}, so "absent" and "set to
 * null" never have to be told apart here. Entries, where a date or an
 * organization can legitimately be cleared, will need more than a null check.
 *
 * <p>{@code displayOrder} is not among them. Moving one section renumbers its
 * neighbours, which is an operation over the whole list rather than a field on
 * one row — {@code POST /sections/reorder} does that.
 */
@Schema(name = "SectionPatch")
public record SectionPatchRequest(
        SectionKind kind,
        @Size(max = 120) String title,
        SectionLayout layout,
        Boolean alwaysInclude,
        Boolean verbatim,
        Boolean active) {

    /** Validation on an absent field would reject leaving it alone. */
    public SectionPatchRequest {
        if (title != null && title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }
}
