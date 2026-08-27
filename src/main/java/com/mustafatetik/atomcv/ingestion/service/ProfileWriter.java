package com.mustafatetik.atomcv.ingestion.service;

import com.mustafatetik.atomcv.ingestion.normalization.NormalizedProfile;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomSource;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.VariantAuthor;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.profile.repository.EntryRepository;
import com.mustafatetik.atomcv.profile.repository.ProfileRepository;
import com.mustafatetik.atomcv.profile.repository.SectionRepository;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A normalised CV written into a profile (Bolum 31.1).
 *
 * <p><strong>Every write goes through a scoped repository</strong>, and the
 * scope comes from {@link ProfileResolver} — the one place a
 * {@code UserContext} becomes a {@code ProfileRef}. Absolute rule 3 is stated
 * that way because a bulk import is exactly where somebody would reach for a
 * raw repository "just this once" and write a hundred rows under an id nobody
 * checked.
 *
 * <p><strong>One transaction.</strong> A CV is one document: a profile holding
 * three of its five sections is not a partial success, it is a profile the
 * person has to notice is wrong. Bolum 31.6's review screen assumes it is
 * looking at the whole thing.
 */
@Service
public class ProfileWriter {

    private static final Logger log = LoggerFactory.getLogger(ProfileWriter.class);

    private final ProfileResolver profiles;
    private final ProfileRepository profileRows;
    private final SectionRepository sections;
    private final EntryRepository entries;
    private final AtomRepository atoms;
    private final AtomVariantRepository variants;

    ProfileWriter(ProfileResolver profiles, ProfileRepository profileRows,
            SectionRepository sections, EntryRepository entries,
            AtomRepository atoms, AtomVariantRepository variants) {
        this.profiles = profiles;
        this.profileRows = profileRows;
        this.sections = sections;
        this.entries = entries;
        this.atoms = atoms;
        this.variants = variants;
    }

    /**
     * @param normalized what Bolum 31.5 produced; never partially written
     * @return the profile the CV was written into
     */
    @Transactional
    public Profile write(UserContext user, NormalizedProfile normalized) {
        var owned = profiles.owned(user);
        Profile profile = owned.profile();
        String language = normalized.language().isBlank()
                ? profile.getSourceLanguage() : normalized.language();

        profile.setContact(normalized.contact());
        profile.setSourceLanguage(language);
        profileRows.save(user, profile);

        var target = new Target(owned.ref(), profile.getId(), language);
        for (var section : normalized.sections()) {
            writeSection(target, section);
        }
        // Counts and a language, never a line of the CV (absolute rule 4).
        log.info("Wrote an imported profile: {}", normalized.shape());
        return profile;
    }

    private void writeSection(Target target, NormalizedProfile.NormalizedSection normalized) {
        Section section = new Section(target.profileId(), normalized.kind(),
                normalized.title(), normalized.displayOrder());
        sections.save(target.ref(), section);

        for (var entry : normalized.entries()) {
            writeEntry(target, section, entry, normalized.kind());
        }
    }

    private void writeEntry(Target target, Section section,
            NormalizedProfile.NormalizedEntry normalized, SectionKind kind) {
        Entry entry = new Entry(target.profileId(), section.getId(),
                normalized.title(), normalized.displayOrder());
        entry.setOrganization(blankToNull(normalized.organization()));
        entry.setLocation(blankToNull(normalized.location()));
        entry.setStartDate(firstOfMonth(normalized.start()));
        entry.setEndDate(firstOfMonth(normalized.end()));
        entries.save(target.ref(), entry);

        for (var atom : normalized.atoms()) {
            writeAtom(target, section, entry, atom, kind);
        }
    }

    /**
     * What an imported atom is, wherever it is about to be kept.
     *
     * <p>Shared with {@code EphemeralProfileWriter}: which kind of atom a
     * section's contents are, and where it came from, are decisions about what
     * a CV means rather than about where it is stored. Two copies would be two
     * answers on the day one of them learned something.
     */
    static Atom atomOf(UUID profileId, Section section, Entry entry,
            NormalizedProfile.NormalizedAtom normalized, SectionKind kind) {
        Atom atom = new Atom(profileId, section.getId(),
                entry == null ? null : entry.getId(), kindOf(kind), normalized.displayOrder());
        atom.setSkills(normalized.skills());
        atom.setMetrics(normalized.metrics());
        atom.setProperNouns(normalized.properNouns());
        // Bolum 14.1: where an atom came from decides what may be done to it,
        // and these are the person's own sentences rather than a model's.
        atom.setSource(AtomSource.CV_UPLOAD);
        return atom;
    }

    /** Likewise: the person wrote it, and Bolum 21.4's staleness reads that. */
    static AtomVariant variantOf(UUID profileId, Atom atom, RichContent content,
            String language, boolean primary) {
        AtomVariant variant = new AtomVariant(profileId, atom.getId(), language, content);
        variant.setPrimary(primary);
        variant.setCreatedBy(VariantAuthor.USER);
        return variant;
    }

    private void writeAtom(Target target, Section section, Entry entry,
            NormalizedProfile.NormalizedAtom normalized, SectionKind kind) {
        Atom atom = atomOf(target.profileId(), section, entry, normalized, kind);
        atoms.save(target.ref(), atom);

        // The source wording is primary: it is what the person wrote and what
        // the review screen shows them.
        writeVariant(target, atom, normalized.source(), target.language(), true);
        if (!normalized.english().isEmpty()) {
            // Only when there is a second one. Bolum 21 reads an absent
            // English variant as "the source is the English", so a duplicate
            // row would be a second copy to keep in step for no gain.
            writeVariant(target, atom, normalized.english(), "en", false);
        }
    }

    private void writeVariant(Target target, Atom atom, RichContent content,
            String language, boolean primary) {
        variants.save(target.ref(),
                variantOf(target.profileId(), atom, content, language, primary));
    }

    /**
     * Which kind of atom a section's contents are (Bolum 13).
     *
     * <p>The extraction does not report one and should not have to: the
     * section it sits under already says what it is, and a model asked for the
     * kind as well would have one more thing to be inconsistent about.
     */
    private static AtomKind kindOf(SectionKind kind) {
        return switch (kind) {
            case SKILLS, SOFT_SKILLS -> AtomKind.SKILL;
            case LANGUAGES -> AtomKind.LANGUAGE;
            case ABOUT -> AtomKind.ABOUT_PARAGRAPH;
            case EDUCATION, EXPERIENCE, PROJECTS, CUSTOM -> AtomKind.BULLET;
        };
    }

    /**
     * A month becomes the first of that month.
     *
     * <p>{@code entries.start_date} is a {@code DATE} (Bolum 13) and a CV gives
     * months. The day is a storage artefact rather than a claim — nothing
     * renders it, and Bolum 31.5 refuses to invent a month for the same reason
     * it would refuse to invent a day.
     */
    static LocalDate firstOfMonth(YearMonth month) {
        return month == null ? null : month.atDay(1);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * What every write below the profile needs, carried rather than held.
     *
     * <p>A field would have been simpler and wrong: this is a singleton, and a
     * second import running concurrently would read the first one's language.
     */
    private record Target(ProfileRef ref, UUID profileId, String language) {
    }
}
