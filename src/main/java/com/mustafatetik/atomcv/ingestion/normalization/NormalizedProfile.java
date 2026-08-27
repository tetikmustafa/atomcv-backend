package com.mustafatetik.atomcv.ingestion.normalization;

import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile.ExtractionWarning;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.time.YearMonth;
import java.util.List;

/**
 * An extracted CV with the code-side work of Bolum 31.5 done to it.
 *
 * <p>Still in memory and still not a profile. Nothing here has an id, and
 * nothing has been written: what this is for is that the review screen of
 * Bolum 31.6 is <em>mandatory</em>, so the shape a person corrects has to exist
 * before any row does. Adim 3.4's fourth slice turns it into
 * {@code Profile}, {@code Section}, {@code Entry} and {@code Atom}.
 *
 * <p>The types are the domain's from here on — {@link Contact},
 * {@link SectionKind}, {@link RichContent}, {@link YearMonth}. That is the
 * boundary this stage exists to cross: everything before it speaks the
 * model's JSON, everything after it speaks the profile's.
 *
 * @param language ISO 639-1, the language every {@code source} run is written
 *                 in
 * @param contact  the domain's own record now, mapped from what was extracted
 * @param sections in the document's order, each already sorted inside
 * @param warnings the model's, plus the ones normalisation itself raised —
 *                 one list, because the review screen does not care which
 *                 stage noticed
 */
public record NormalizedProfile(
        String language,
        Contact contact,
        List<NormalizedProfile.NormalizedSection> sections,
        List<ExtractionWarning> warnings) {

    public NormalizedProfile {
        language = language == null ? "" : language;
        contact = contact == null ? Contact.EMPTY : contact;
        sections = sections == null ? List.of() : List.copyOf(sections);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** Every atom, which is what a completeness score and a quota count. */
    public List<NormalizedAtom> atoms() {
        return sections.stream()
                .flatMap(section -> section.entries().stream())
                .flatMap(entry -> entry.atoms().stream())
                .toList();
    }

    /** Statistics only; the CV's own words never reach a log (absolute rule 4). */
    public String shape() {
        return "language=" + language
                + " sections=" + sections.size()
                + " entries=" + sections.stream().mapToInt(s -> s.entries().size()).sum()
                + " atoms=" + atoms().size()
                + " warnings=" + warnings.size();
    }

    /**
     * @param displayOrder its place among the sections, assigned here rather
     *                     than left to insertion order — Bolum 13 stores it,
     *                     and a column that is sometimes right is worse than
     *                     one that is always written
     */
    public record NormalizedSection(
            SectionKind kind,
            String title,
            short displayOrder,
            List<NormalizedEntry> entries) {

        public NormalizedSection {
            entries = List.copyOf(entries);
        }
    }

    /**
     * @param start null when the date could not be read <em>or</em> was not
     *              given; either way a warning says so and the review screen
     *              asks
     * @param end   null also means the person is still there, which is not a
     *              warning
     */
    public record NormalizedEntry(
            String title,
            String organization,
            String location,
            YearMonth start,
            YearMonth end,
            short displayOrder,
            List<NormalizedAtom> atoms) {

        public NormalizedEntry {
            atoms = List.copyOf(atoms);
        }

        /** Bolum 31.6 opens a section for an entry that still needs a date. */
        public boolean isOngoing() {
            return end == null;
        }
    }

    /**
     * One fact, in up to two languages, marked.
     *
     * @param source  the sentence as the CV wrote it, cut into runs
     * @param english its English rendering, or {@link RichContent#EMPTY} when
     *                the CV was already English — Bolum 21 reads the absence as
     *                "the source is the English", not as a gap
     * @param tags    canonical, and the profile's own vocabulary (Bolum 19.2)
     */
    public record NormalizedAtom(
            RichContent source,
            RichContent english,
            List<String> skills,
            List<String> metrics,
            List<String> properNouns,
            List<String> tags,
            short displayOrder) {

        public NormalizedAtom {
            skills = List.copyOf(skills);
            metrics = List.copyOf(metrics);
            properNouns = List.copyOf(properNouns);
            tags = List.copyOf(tags);
        }
    }
}
