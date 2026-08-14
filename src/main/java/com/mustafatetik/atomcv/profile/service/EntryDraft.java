package com.mustafatetik.atomcv.profile.service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** An entry to create, in domain terms. Its position is decided by the service. */
public record EntryDraft(
        UUID sectionId,
        String title,
        String organization,
        String location,
        LocalDate startDate,
        LocalDate endDate,
        String url,
        float importance,
        boolean alwaysInclude,
        boolean verbatim,
        short minAtoms) {

    public EntryDraft {
        Objects.requireNonNull(sectionId, "sectionId");
        Objects.requireNonNull(title, "title");
    }
}
