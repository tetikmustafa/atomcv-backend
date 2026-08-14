package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** An atom to create, together with the wording it starts with. */
public record AtomDraft(
        UUID sectionId,
        UUID entryId,
        AtomKind kind,
        RichContent content,
        String language,
        float importance,
        boolean alwaysInclude,
        boolean verbatim,
        List<String> skills,
        List<String> metrics,
        List<String> properNouns) {

    public AtomDraft {
        Objects.requireNonNull(sectionId, "sectionId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(content, "content");
        language = language == null ? "en" : language;
        skills = skills == null ? List.of() : List.copyOf(skills);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        properNouns = properNouns == null ? List.of() : List.copyOf(properNouns);
    }
}
