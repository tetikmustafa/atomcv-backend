package com.mustafatetik.atomcv.ingestion.normalization;

import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile.ExtractionWarning;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractionWarningCode;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.Tag;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.RunMarking;
import com.mustafatetik.atomcv.shared.text.SkillNames;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bolum 31.5's seven steps, all of which the code does and none of which the
 * model is asked to.
 *
 * <p>The split is the point. A model is good at reading a document and bad at
 * being consistent about a dictionary, a date format or an ordering — asked
 * for those it will comply most of the time, which is the worst possible rate
 * for something a later comparison depends on being exact. So the model
 * reports what the document says, and everything that has to be the same
 * across two documents is decided here.
 *
 * <p>Two of the seven were already built. {@code plainText} and
 * {@code contentHash} are {@link RichContent}'s own, computed over the plain
 * text so that re-marking a sentence does not invalidate its embedding
 * (Bolum 16.2); and tag canonicalisation is {@link Tag#canonical}, which the
 * column already enforces.
 */
@Component
public class ProfileNormalizer {

    private static final Logger log = LoggerFactory.getLogger(ProfileNormalizer.class);

    /**
     * The kinds a CV lists newest first (Bolum 31.5, step 6).
     *
     * <p>Education is deliberately not among them. A CV lists degrees newest
     * first too, but a reader scanning one expects the highest qualification
     * at the top and those are not always the same row — reordering what the
     * document already decided would be the system overruling the person for
     * no gain.
     */
    private static final Set<SectionKind> NEWEST_FIRST =
            Set.of(SectionKind.EXPERIENCE, SectionKind.PROJECTS);

    /**
     * @param extracted what the model returned, already gated
     * @return the same document with dates read, skills and tags canonical,
     *         emphasis turned into runs, entries ordered and every
     *         {@code display_order} written
     */
    public NormalizedProfile normalize(ExtractedProfile extracted) {
        List<ExtractionWarning> warnings = new ArrayList<>(extracted.warnings());
        List<NormalizedProfile.NormalizedSection> sections = new ArrayList<>();

        short sectionOrder = 0;
        for (var section : extracted.sections()) {
            sections.add(normalizeSection(section, sectionOrder++, warnings));
        }

        var normalized = new NormalizedProfile(
                extracted.detectedLanguage(), contactOf(extracted.contact()), sections, warnings);
        log.info("Normalized an extracted profile: {}", normalized.shape());
        return normalized;
    }

    /**
     * An entry and whatever could not be settled about it, before either has a
     * position (F-018).
     *
     * <p>The warnings travel with their entry through the sort rather than
     * being written down against the index the model happened to use. That
     * index does not survive: {@code newestFirst} reorders the entries a line
     * later, and {@code display_order} is rewritten from the new positions.
     */
    private record Located(
            NormalizedProfile.NormalizedEntry entry, List<ExtractionWarning> warnings) {
    }

    private NormalizedProfile.NormalizedSection normalizeSection(
            ExtractedProfile.ExtractedSection section, short order,
            List<ExtractionWarning> warnings) {

        List<Located> located = new ArrayList<>();
        for (var raw : section.entries()) {
            located.add(normalizeEntry(raw, section.kind()));
        }
        if (NEWEST_FIRST.contains(section.kind())) {
            located.sort(Comparator.comparing(Located::entry, newestFirst()));
        }
        // After the sort, never before: display_order is the order a reader
        // sees, and writing it first would record the order the model
        // happened to answer in. The paths are written here for the same
        // reason -- a warning pointing at the model's order points at nothing.
        List<NormalizedProfile.NormalizedEntry> ordered = new ArrayList<>();
        for (short i = 0; i < located.size(); i++) {
            ordered.add(withOrder(located.get(i).entry(), i));
            String path = EntryPath.of(order, i);
            located.get(i).warnings().forEach(warning -> warnings.add(
                    new ExtractionWarning(warning.code(), warning.detail(), path)));
        }
        return new NormalizedProfile.NormalizedSection(
                section.kind(), section.title().strip(), order, ordered);
    }

    /**
     * Newest first, and an entry with no start date last.
     *
     * <p>Not first: a missing date is the least information, and putting it at
     * the top would give the row the position a reader reads as "most recent".
     * A comparator that returned it first would also make the ordering depend
     * on how many dates failed to parse, which is a property of the file
     * rather than of the career.
     */
    private static Comparator<NormalizedProfile.NormalizedEntry> newestFirst() {
        return Comparator.comparing(NormalizedProfile.NormalizedEntry::start,
                Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private Located normalizeEntry(
            ExtractedProfile.ExtractedEntry entry, SectionKind kind) {

        // Raised without a path and given one after the sort: where this entry
        // ends up is not known yet, and the model's own order is not where.
        List<ExtractionWarning> warnings = new ArrayList<>();
        YearMonth start = dateOf(entry.startDate(), "startDate", warnings);
        YearMonth end = dateOf(entry.endDate(), "endDate", warnings);

        if (entry.organization().isBlank() && !entry.atoms().isEmpty()
                && kind != SectionKind.ABOUT && kind != SectionKind.SKILLS) {
            warnings.add(new ExtractionWarning(ExtractionWarningCode.MISSING_ORGANIZATION,
                    "an entry has content but no organization", ""));
        }
        if (start != null && end != null && end.isBefore(start)) {
            warnings.add(new ExtractionWarning(ExtractionWarningCode.OVERLAPPING_DATES,
                    "an entry ends before it starts", ""));
        }

        List<NormalizedProfile.NormalizedAtom> atoms = new ArrayList<>();
        short order = 0;
        for (var atom : entry.atoms()) {
            atoms.add(normalizeAtom(atom, order++));
        }
        return new Located(new NormalizedProfile.NormalizedEntry(
                entry.title().strip(), entry.organization().strip(), entry.location().strip(),
                start, end, (short) 0, atoms), List.copyOf(warnings));
    }

    /**
     * A date, or null and a warning saying which field could not be read.
     *
     * <p><strong>An absent date raises nothing.</strong> A CV that gives no end
     * date is saying the person is still there, and a warning on every current
     * job would train people to click past the one that matters.
     */
    private static YearMonth dateOf(String written, String field,
            List<ExtractionWarning> warnings) {
        if (written == null || written.isBlank()) {
            return null;
        }
        Optional<YearMonth> parsed = PartialDates.parse(written);
        if (parsed.isPresent()) {
            return parsed.get();
        }
        // The field name and the reason, never the value: a date is not
        // content, but it is on a document that is (absolute rule 4), and a
        // year beside an employer identifies a person more than either alone.
        warnings.add(new ExtractionWarning(ExtractionWarningCode.AMBIGUOUS_DATE,
                PartialDates.isYearOnly(written)
                        ? field + " gave a year with no month"
                        : field + " could not be read as a date",
                ""));
        return null;
    }

    private NormalizedProfile.NormalizedAtom normalizeAtom(
            ExtractedProfile.ExtractedAtom atom, short order) {

        List<String> skills = canonicalSkills(atom.skills());
        List<String> metrics = List.copyOf(atom.metrics());

        RichContent source = RunMarking.mark(
                atom.textSource(), atom.emphasisSource(), skills, metrics);
        // Empty and not a copy of the source: Bolum 21 reads an absent English
        // variant as "the source is the English", and a duplicate would be a
        // second row to keep in step for no gain.
        RichContent english = atom.textEn() == null || atom.textEn().isBlank()
                ? RichContent.EMPTY
                : RunMarking.mark(atom.textEn(), atom.emphasisEn(), skills, metrics);

        return new NormalizedProfile.NormalizedAtom(
                source, english, skills, metrics,
                List.copyOf(atom.properNouns()), canonicalTags(atom.tags()), order);
    }

    /**
     * Canonical and de-duplicated, keeping the model's order.
     *
     * <p>A {@code LinkedHashSet} rather than {@code Set.copyOf}: this list
     * reaches a JSON column and an assertion, and the JDK's immutable sets
     * iterate in an order salted per JVM run.
     */
    private static List<String> canonicalSkills(List<String> skills) {
        Set<String> canonical = new LinkedHashSet<>();
        for (String skill : skills) {
            String name = SkillNames.canonical(skill);
            if (!name.isEmpty()) {
                canonical.add(name);
            }
        }
        return List.copyOf(canonical);
    }

    private static List<String> canonicalTags(List<String> tags) {
        Set<String> canonical = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                // The column's own rule, so a tag written here and a tag typed
                // into the editor are one row (Bolum 19.2).
                canonical.add(Tag.canonical(tag));
            }
        }
        return List.copyOf(canonical);
    }

    private static NormalizedProfile.NormalizedEntry withOrder(
            NormalizedProfile.NormalizedEntry entry, short order) {
        return new NormalizedProfile.NormalizedEntry(
                entry.title(), entry.organization(), entry.location(),
                entry.start(), entry.end(), order, entry.atoms());
    }

    private static Contact contactOf(ExtractedProfile.ExtractedContact extracted) {
        return new Contact(
                trimmed(extracted.name()), trimmed(extracted.email()), trimmed(extracted.phone()),
                trimmed(extracted.linkedin()), trimmed(extracted.github()),
                trimmed(extracted.website()), trimmed(extracted.location()));
    }

    /** Blank becomes null, so {@code Contact.isEmpty} means what it says. */
    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
