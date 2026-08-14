package com.mustafatetik.atomcv.rendering.model;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Everything a renderer is allowed to know (Bolum 22.2).
 *
 * <p><strong>No atom id, no score, no lock.</strong> The renderer does not
 * know why this content was chosen, and cannot be tempted to make a selection
 * decision of its own — selection happened in Faz C and is finished.
 */
public record RenderRequest(
        ProfileHeader header,
        List<RenderableSection> sections,
        TemplateCustomization customization,
        Locale contentLanguage) {

    public RenderRequest {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(customization, "customization");
        sections = sections == null ? List.of() : List.copyOf(sections);
        contentLanguage = contentLanguage == null ? Locale.ENGLISH : contentLanguage;
    }

    /** The name and contact line at the top of the page. */
    public record ProfileHeader(
            String name,
            String headline,
            List<String> contactLines) {

        public ProfileHeader {
            name = name == null ? "" : name;
            contactLines = contactLines == null ? List.of() : List.copyOf(contactLines);
        }

        /** Shape only — every field here is personal data. */
        @Override
        public String toString() {
            return "ProfileHeader[contactLines=" + contactLines.size() + "]";
        }
    }

    /** One heading with what hangs off it, already in the order it prints. */
    public record RenderableSection(
            String title,
            List<RenderableEntry> entries,
            List<RichContent> atoms) {

        public RenderableSection {
            title = title == null ? "" : title;
            entries = entries == null ? List.of() : List.copyOf(entries);
            atoms = atoms == null ? List.of() : List.copyOf(atoms);
        }
    }

    /** One position, degree or project, with the bullets that were selected. */
    public record RenderableEntry(
            String title,
            String organization,
            String location,
            String dateRange,
            List<RichContent> atoms) {

        public RenderableEntry {
            title = title == null ? "" : title;
            atoms = atoms == null ? List.of() : List.copyOf(atoms);
        }

        /** Shape only: title, organization and location are user content. */
        @Override
        public String toString() {
            return "RenderableEntry[atoms=" + atoms.size() + "]";
        }
    }
}
