package com.mustafatetik.atomcv.ingestion.structuring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import java.util.List;

/**
 * What one call reads out of a CV (Bolum 31.4).
 *
 * <p><strong>One call, both languages.</strong> Bolum 31.4 is explicit that the
 * English rendering is produced here and not by a second translation step: the
 * model has the whole document in front of it, and a later pass would be
 * translating a sentence stripped of the context that made it mean something.
 *
 * <p><strong>{@code SectionKind} is the domain's own; the contact block is
 * not.</strong> A parallel vocabulary for the section kinds would be two lists
 * to keep in step, and a schema test holds them together. The domain's
 * {@code Contact} looked like the same kind of reuse and is not: it is a JSONB
 * column, so it refuses an unknown key on purpose — reading a row back with a
 * field silently dropped is how a rename loses data. Here the opposite rule
 * applies, and the contact block is where a model is most likely to invent a
 * field (a portfolio, an address, a second handle). The two records have the
 * same shape and answer to different rules, so they are two records, and
 * Bolum 31.5 maps one to the other.
 *
 * <p>Everything below stays ingestion's for a plainer reason: an extracted atom
 * is not yet an {@code Atom}. It has no id, no runs and no embedding until
 * Bolum 31.5 gives it them.
 *
 * <p>Unknown fields are ignored rather than refused. A model that adds one has
 * not failed, and what matters is judged by the schema and by
 * {@link StructuringAudit}.
 *
 * @param detectedLanguage   ISO 639-1, the language the CV is written in
 * @param languageConfidence how sure the model is; below the floor in
 *                           {@link ProfileStructuring} the user is asked rather
 *                           than guessed at (Bolum 31.10)
 * @param contact            the header block, mapped onto the domain's own
 *                           record by Bolum 31.5
 * @param sections           the document's structure, in the order it was read
 * @param warnings           what the model could not settle, for the review
 *                           screen of Bolum 31.6 to open on
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedProfile(
        String detectedLanguage,
        double languageConfidence,
        ExtractedProfile.ExtractedContact contact,
        List<ExtractedProfile.ExtractedSection> sections,
        List<ExtractedProfile.ExtractionWarning> warnings) {

    public ExtractedProfile {
        detectedLanguage = detectedLanguage == null ? "" : detectedLanguage;
        contact = contact == null ? ExtractedContact.EMPTY : contact;
        sections = sections == null ? List.of() : List.copyOf(sections);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** Every atom in the document, which is what Bolum 31.10 counts. */
    public List<ExtractedAtom> atoms() {
        return sections.stream()
                .flatMap(section -> section.entries().stream())
                .flatMap(entry -> entry.atoms().stream())
                .toList();
    }

    /**
     * What may be said about an extraction in a log line (absolute rule 4).
     *
     * <p>Counts and a language code. Not one word of the CV and not a section
     * title — a title is often a job title, and a job title is one of the more
     * identifying things a document carries.
     */
    public String shape() {
        return "language=" + detectedLanguage
                + " confidence=" + languageConfidence
                + " sections=" + sections.size()
                + " entries=" + sections.stream().mapToInt(s -> s.entries().size()).sum()
                + " atoms=" + atoms().size()
                + " warnings=" + warnings.size();
    }

    /**
     * The header block, in the same shape the domain stores it.
     *
     * <p>Tolerant of fields nobody asked for, which is the whole reason it is
     * not the domain's own record: a model offering a portfolio link has not
     * failed, and Bolum 31.5 drops what it cannot map.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedContact(
            String name,
            String email,
            String phone,
            String linkedin,
            String github,
            String website,
            String location) {

        static final ExtractedContact EMPTY =
                new ExtractedContact(null, null, null, null, null, null, null);

        /** Not a JSON property: this record is only ever read, never written. */
        @JsonIgnore
        public boolean isEmpty() {
            return name == null && email == null && phone == null && linkedin == null
                    && github == null && website == null && location == null;
        }
    }

    /**
     * @param kind    which of Bolum 13's kinds this is
     * @param title   the heading as the CV wrote it, in the source language
     * @param entries the rows under it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedSection(SectionKind kind, String title, List<ExtractedEntry> entries) {

        public ExtractedSection {
            kind = kind == null ? SectionKind.CUSTOM : kind;
            title = title == null ? "" : title;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    /**
     * One job, degree or project.
     *
     * @param startDate {@code YYYY-MM}, or null. <strong>Never invented</strong> —
     *                  Bolum 31.5 leaves an unparseable date null and raises a
     *                  warning, because a plausible wrong date is the kind of
     *                  error nobody proofreads out
     * @param endDate   likewise; null also means the person is still there
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedEntry(
            String title,
            String organization,
            String location,
            String startDate,
            String endDate,
            List<ExtractedAtom> atoms) {

        public ExtractedEntry {
            title = title == null ? "" : title;
            organization = organization == null ? "" : organization;
            location = location == null ? "" : location;
            atoms = atoms == null ? List.of() : List.copyOf(atoms);
        }
    }

    /**
     * One bullet, in both languages, with the parts of it that matter marked.
     *
     * @param textSource     the sentence as the CV wrote it
     * @param textEn         its English rendering, or null when the source is
     *                       already English. Bolum 31.4 does not ask for the
     *                       second field in that case; a schema cannot make a
     *                       field conditional, so it is nullable and the prompt
     *                       says when to leave it out
     * @param emphasisSource the substrings worth bolding, quoted from
     *                       {@code textSource}. Bolum 31.5 turns them into runs
     *                       by first match, so they have to be exact quotations
     * @param emphasisEn     likewise, against {@code textEn}
     * @param skills         canonical, lowercase, English
     * @param metrics        the numbers the sentence claims, as written
     * @param properNouns    names that must not be translated or reworded
     * @param tags           free-form labels the review screen groups on
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedAtom(
            String textSource,
            String textEn,
            List<String> emphasisSource,
            List<String> emphasisEn,
            List<String> skills,
            List<String> metrics,
            List<String> properNouns,
            List<String> tags) {

        public ExtractedAtom {
            textSource = textSource == null ? "" : textSource;
            emphasisSource = emphasisSource == null ? List.of() : List.copyOf(emphasisSource);
            emphasisEn = emphasisEn == null ? List.of() : List.copyOf(emphasisEn);
            skills = skills == null ? List.of() : List.copyOf(skills);
            metrics = metrics == null ? List.of() : List.copyOf(metrics);
            properNouns = properNouns == null ? List.of() : List.copyOf(properNouns);
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    /**
     * Something the model could not settle (Bolum 31.4, Bolum 31.6).
     *
     * @param code   a closed vocabulary, so the frontend resolves one ICU key
     *               with a {@code select} rather than printing a server
     *               sentence — the shape F-016 asked for
     * @param detail a short English note for the operator, never the CV's text
     * @param path   where it happened, as a pointer into this record, so the
     *               review screen can open the right row
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractionWarning(ExtractionWarningCode code, String detail, String path) {

        public ExtractionWarning {
            detail = detail == null ? "" : detail;
            path = path == null ? "" : path;
        }
    }
}
