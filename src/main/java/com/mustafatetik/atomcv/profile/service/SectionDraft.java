package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import java.util.Objects;

/** A section to create. Its position is decided by the service, not the caller. */
public record SectionDraft(
        SectionKind kind,
        String title,
        SectionLayout layout,
        boolean alwaysInclude,
        boolean verbatim) {

    public SectionDraft {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        layout = layout == null ? SectionLayout.BULLET_LIST : layout;
    }
}
