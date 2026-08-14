package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;

/**
 * Fields to change on a section. Null means "leave it as it is" — every column
 * behind these is {@code NOT NULL}, so nothing here can legitimately be
 * cleared.
 */
public record SectionPatch(
        SectionKind kind,
        String title,
        SectionLayout layout,
        Boolean alwaysInclude,
        Boolean verbatim,
        Boolean active) {
}
