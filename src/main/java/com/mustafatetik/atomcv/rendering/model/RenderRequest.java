package com.mustafatetik.atomcv.rendering.model;

import com.mustafatetik.atomcv.profile.domain.SectionLayout;
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

    /**
     * One line of the contact block: what it is, what it says, and where it
     * points.
     *
     * @param label the reader's word for this field, already in the content
     *              language. Chosen from a closed set by {@link ContactKind},
     *              never from user text
     * @param value what is printed
     * @param href  where it links, or empty for something unlinkable like a
     *              city or a phone number
     */
    public record ContactLine(String label, String value, String href) {

        public ContactLine {
            label = label == null ? "" : label;
            value = value == null ? "" : value;
            href = href == null ? "" : href;
        }

        /** Shape only: a contact line is personal data. */
        @Override
        public String toString() {
            return "ContactLine[" + label + "]";
        }
    }

    /** The name and contact line at the top of the page. */
    public record ProfileHeader(
            String name,
            String headline,
            List<ContactLine> contactLines) {

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

    /**
     * One heading with what hangs off it, already in the order it prints.
     *
     * @param layout how Bolum 33.4 says this section is set. It was missing
     *               here entirely, so the renderer set every section as a
     *               bullet list whatever the column said — {@code INLINE_LIST}
     *               and {@code TWO_COLUMN} existed in the enum, in the schema
     *               and in the CHECK constraint, and could not reach the page.
     */
    public record RenderableSection(
            String title,
            SectionLayout layout,
            List<RenderableEntry> entries,
            List<RichContent> atoms) {

        public RenderableSection {
            title = title == null ? "" : title;
            layout = layout == null ? SectionLayout.BULLET_LIST : layout;
            entries = entries == null ? List.of() : List.copyOf(entries);
            atoms = atoms == null ? List.of() : List.copyOf(atoms);
        }

        /** Shape only: a section title is the user's own wording. */
        @Override
        public String toString() {
            return "RenderableSection[entries=" + entries.size() + ", atoms=" + atoms.size() + "]";
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
