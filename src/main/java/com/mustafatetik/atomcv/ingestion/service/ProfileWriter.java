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
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.VariantAuthor;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.repository.AnonymousProfiles;
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
import java.time.Instant;
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
    private final AnonymousProfiles anonymous;
    private final SectionRepository sections;
    private final EntryRepository entries;
    private final AtomRepository atoms;
    private final AtomVariantRepository variants;

    ProfileWriter(ProfileResolver profiles, ProfileRepository profileRows,
            AnonymousProfiles anonymous,
            SectionRepository sections, EntryRepository entries,
            AtomRepository atoms, AtomVariantRepository variants) {
        this.anonymous = anonymous;
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
    /**
     * @param replace what the caller answered to {@code PROFILE_ALREADY_EXISTS}
     *                (Bolum 08b): the profile that is there is discarded first,
     *                so the CV becomes the profile rather than being added to
     *                it. Without it a second import wrote its sections beside
     *                the first import's, and the person found out in the editor
     */
    @Transactional
    public Profile write(UserContext user, NormalizedProfile normalized, boolean replace) {
        var owned = profiles.owned(user);
        Profile profile = owned.profile();
        if (replace) {
            clear(owned.ref());
        }
        String language = normalized.language().isBlank()
                ? profile.getSourceLanguage() : normalized.language();

        profile.setContact(normalized.contact());
        profile.setSourceLanguage(language);
        profileRows.save(user, profile);

        writeTree(new Target(owned.ref(), profile.getId(), language), normalized);
        // Counts and a language, never a line of the CV (absolute rule 4).
        log.info("Wrote an imported profile: {}", normalized.shape());
        return profile;
    }

    /**
     * The same CV, written into an anonymous session's profile (Bolum 9).
     *
     * <p><strong>The same method body, and that is the point of it being
     * here.</strong> This used to be {@code EphemeralProfileWriter}, a second
     * implementation over a Redis document, and it had already drifted from this
     * one — its own comment records the cost: a Languages heading printed twice
     * and a summary set under a title nobody wrote. An anonymous upload is the
     * same CV read the same way, so it is now the same code, and the only thing
     * that differs is which row the head is and when it stops existing.
     *
     * <p><strong>A second import always replaces.</strong> Bolum 31.6.3 gives a
     * session one document, so there is no {@code replace} flag to answer: the
     * question {@code PROFILE_ALREADY_EXISTS} asks an account is a question
     * about work worth keeping, and an anonymous session's previous upload is
     * the same person changing their mind two minutes ago.
     *
     * @param expiresAt when the session ends, pushed out on every write
     */
    @Transactional
    public Profile writeAnonymously(
            ProfileRef ref, Instant expiresAt, NormalizedProfile normalized) {

        Profile profile = anonymous.findOrCreate(ref, expiresAt);
        clear(ref);

        String language = normalized.language().isBlank()
                ? profile.getSourceLanguage() : normalized.language();
        profile.setContact(normalized.contact());
        profile.setSourceLanguage(language);
        anonymous.save(ref, profile);

        writeTree(new Target(ref, profile.getId(), language), normalized);
        // Counts and a language, never a line of the CV (absolute rule 4).
        log.info("Wrote an anonymous profile: {}", normalized.shape());
        return profile;
    }

    private void writeTree(Target target, NormalizedProfile normalized) {
        for (var section : normalized.sections()) {
            writeSection(target, section);
        }
    }

    /**
     * Everything under the profile, in the order the foreign keys allow —
     * variants before atoms, atoms before entries, entries before sections,
     * which is the reverse of the order they are written in.
     *
     * <p>The profile row itself stays. Its id is referenced by generations and
     * by the contact block the person did not ask to lose, and deleting it
     * would cascade into history that is not part of this CV.
     */
    private void clear(ProfileRef ref) {
        variants.findAll(ref).forEach(variant -> variants.delete(ref, variant));
        atoms.findAll(ref).forEach(atom -> atoms.delete(ref, atom));
        entries.findAll(ref).forEach(entry -> entries.delete(ref, entry));
        sections.findAll(ref).forEach(section -> sections.delete(ref, section));
    }

    private void writeSection(Target target, NormalizedProfile.NormalizedSection normalized) {
        Section section = new Section(target.profileId(), normalized.kind(),
                normalized.title(), normalized.displayOrder());
        section.setLayout(layoutFor(normalized.kind()));
        sections.save(target.ref(), section);

        if (hangsOffItsSection(normalized.kind())) {
            writeSummary(target, section, normalized);
            return;
        }
        for (var entry : normalized.entries()) {
            writeEntry(target, section, entry, normalized.kind());
        }
    }

    /**
     * Whether a kind's atoms hang off the section rather than off an entry.
     *
     * <p>Shared with {@code EphemeralProfileWriter} for the reason
     * {@link #atomOf} is: where a section's contents live is a decision about
     * what a CV means rather than about where it is stored, and two copies
     * would be two answers on the day one of them learned something. That day
     * came — the persistent writer learned about layouts, minimums and this,
     * and an anonymous upload kept producing the shape all three fixed.
     */
    static boolean hangsOffItsSection(SectionKind kind) {
        return kind == SectionKind.ABOUT;
    }

    /**
     * A summary hangs off its section, not off an entry (Bolum 20.2).
     *
     * <p>Extraction has to put every atom somewhere and the shape it is given
     * has only entries, so it invents a title for the one it makes — and a real
     * import produced <em>Professional Summary</em>, which was printed as a
     * heading above the paragraph. The document it was read from has no such
     * line: its summary sits straight under the section heading, the way every
     * CV's does. So the page carried a heading nobody wrote, and paid an entry
     * heading's 21 pt for it.
     *
     * <p>Renumbered as one run. Each entry numbers its own atoms from zero, so
     * flattening two of them without this would put two atoms at position 0 and
     * leave the order of the section to whatever the database returned. A person
     * keeping several summaries — one written towards backend work, one towards
     * data — wrote them in an order, and only one of them is going to be printed
     * (see {@code SectionFloor}'s ceiling), so which one is first decides which
     * one that is.
     */
    private void writeSummary(Target target, Section section,
            NormalizedProfile.NormalizedSection normalized) {

        short order = 0;
        for (var entry : normalized.entries()) {
            for (var atom : entry.atoms()) {
                writeParagraph(target, section, atom, order++);
            }
        }
    }

    private void writeParagraph(Target target, Section section,
            NormalizedProfile.NormalizedAtom normalized, short order) {

        Atom atom = atomOf(target.profileId(), section, null, normalized, SectionKind.ABOUT);
        atom.setDisplayOrder(order);
        atoms.save(target.ref(), atom);

        writeVariant(target, atom, normalized.source(), target.language(), true);
        if (!normalized.english().isEmpty()) {
            writeVariant(target, atom, normalized.english(), "en", false);
        }
    }

    /**
     * How a section is set (Bolum 33.4).
     *
     * <p>The column has allowed four layouts since the first migration and the
     * importer wrote the default over all of them, so every profile in
     * existence said {@code bullet_list} — including the skills matrices that
     * are the whole reason {@code INLINE_LIST} is in the enum. A Tech Stack
     * with one category per bullet is a list of five lines where the person
     * wrote five labelled rows.
     *
     * <p><strong>Languages is the second, and for the same reason.</strong> A
     * language is a label and a level — "Turkish: Native" — which is the row a
     * skills matrix is made of. Set as entries it printed the label twice: an
     * entry heading reading {@code English} with a bullet under it reading
     * {@code English: B2}, and two languages then cost 112 pt of a 708 pt page
     * for what an inline list prints in 45.
     *
     * <p><strong>About is the third, and it is neither of the other two.</strong>
     * A summary is one flowing paragraph — that is what it is in the document
     * this template was taken from, and in every CV that has one — and under
     * the default it was printed as a bulleted item: a marker in front of a
     * paragraph, which reads as the first of a list that never arrives. It is
     * not an inline list either. An inline row is a label and the list it
     * introduces, so its first colon is set in bold, and a summary opening
     * "Backend engineer: five years of ..." would have had its first words
     * emboldened by a rule that was never about it. {@code PARAGRAPH} says the
     * one thing that is true of it: prose, no marker.
     *
     * <p>Experience, projects and education stay bullets and entries, which is
     * what the default already says. {@code TWO_COLUMN} stays unused here —
     * Bolum 33.5 keeps Classic single-column for ATS extraction, and choosing
     * it at import would decide that question in the wrong place.
     */
    static SectionLayout layoutFor(SectionKind kind) {
        return switch (kind) {
            case SKILLS, LANGUAGES -> SectionLayout.INLINE_LIST;
            case ABOUT -> SectionLayout.PARAGRAPH;
            default -> SectionLayout.BULLET_LIST;
        };
    }

    /**
     * The floor an imported entry can actually reach (Bolum 20.2, constraint 4).
     *
     * <p>{@code min_atoms} says how much of an entry is worth printing, and its
     * column default is two. An import that leaves the default in place writes
     * that claim over entries extraction gave one bullet — a language, a degree,
     * a Tech Stack category — and for those the rule stops meaning "show at
     * least this much" and starts meaning "never show this at all". Faz C then
     * drops them whole, and a real profile lost its Tech Stack, Languages and
     * Education sections to a default nobody chose.
     *
     * <p>The unreachable minimum stays available deliberately: a user who edits
     * an entry to ask for two bullets it does not have is asking for it to be
     * dropped, and {@code SelectionPhase} still obeys that. This only stops the
     * importer from making that choice on the user's behalf.
     *
     * <p><strong>An About entry is one paragraph, whatever the default says.</strong>
     * The column default of two is a bullet-list number, and a summary is not a
     * bullet list. A profile keeping four summaries — one written towards
     * backend work, one towards data, one towards AI, which is what a person
     * maintaining a master CV does — had its About entry claim a minimum of
     * two, so Bolum 20.3's "prints its minimum or none of itself" put two
     * opening paragraphs on one page. Both were the person's own words and the
     * document still read as a mistake.
     */
    static short reachableMinimumFor(SectionKind kind, int atomCount) {
        int wanted = kind == SectionKind.ABOUT ? 1 : Entry.DEFAULT_MIN_ATOMS;
        return (short) Math.min(wanted, atomCount);
    }

    private void writeEntry(Target target, Section section,
            NormalizedProfile.NormalizedEntry normalized, SectionKind kind) {
        Entry entry = new Entry(target.profileId(), section.getId(),
                normalized.title(), normalized.displayOrder());
        entry.setOrganization(blankToNull(normalized.organization()));
        entry.setLocation(blankToNull(normalized.location()));
        entry.setStartDate(firstOfMonth(normalized.start()));
        entry.setEndDate(firstOfMonth(normalized.end()));
        entry.setMinAtoms(reachableMinimumFor(kind, normalized.atoms().size()));
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
