package com.mustafatetik.atomcv.ingestion.service;

import com.mustafatetik.atomcv.ingestion.normalization.NormalizedProfile;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.service.EphemeralProfile;
import com.mustafatetik.atomcv.profile.service.EphemeralProfileStore;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The same CV, written where an anonymous person's profile lives (Bolum 9).
 *
 * <p><strong>The mapping is {@link ProfileWriter}'s and is not repeated
 * here.</strong> Which kind of atom a section's contents are, and how a month
 * becomes a date, are decisions about what a CV means — not about where it is
 * kept — so they stay in one place and this builds the same objects the
 * persistent path builds. What differs is the last line: four lists into one
 * Redis value instead of four tables.
 *
 * <p>No transaction, and there is nothing to make one out of: the whole
 * profile is a single write. That is the property the store was chosen for —
 * an anonymous profile cannot be half-written, so it cannot be half-read
 * either.
 */
@Service
public class EphemeralProfileWriter {

    private static final Logger log = LoggerFactory.getLogger(EphemeralProfileWriter.class);

    private final EphemeralProfileStore store;

    EphemeralProfileWriter(EphemeralProfileStore store) {
        this.store = store;
    }

    public EphemeralProfile write(ProfileRef profile, NormalizedProfile normalized) {
        UUID profileId = profile.id();
        List<Section> sections = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        List<Atom> atoms = new ArrayList<>();
        List<AtomVariant> variants = new ArrayList<>();

        String language = normalized.language().isBlank() ? "en" : normalized.language();
        for (var section : normalized.sections()) {
            Section row = new Section(profileId, section.kind(),
                    section.title(), section.displayOrder());
            sections.add(row);
            for (var entry : section.entries()) {
                Entry entryRow = new Entry(profileId, row.getId(),
                        entry.title(), entry.displayOrder());
                entryRow.setOrganization(blankToNull(entry.organization()));
                entryRow.setLocation(blankToNull(entry.location()));
                entryRow.setStartDate(ProfileWriter.firstOfMonth(entry.start()));
                entryRow.setEndDate(ProfileWriter.firstOfMonth(entry.end()));
                entries.add(entryRow);
                for (var atom : entry.atoms()) {
                    addAtom(profileId, row, entryRow, atom, section.kind(), language,
                            atoms, variants);
                }
            }
        }

        var stored = new EphemeralProfile(profileId,
                normalized.contact(), language,
                sections, entries, atoms, variants);
        store.save(profile, stored);
        // Counts, never a line of the CV (absolute rule 4).
        log.info("Wrote an anonymous profile: {}", stored.shape());
        return stored;
    }

    private static void addAtom(UUID profileId, Section section, Entry entry,
            NormalizedProfile.NormalizedAtom normalized, SectionKind kind, String language,
            List<Atom> atoms, List<AtomVariant> variants) {

        Atom atom = ProfileWriter.atomOf(profileId, section, entry, normalized, kind);
        atoms.add(atom);
        variants.add(ProfileWriter.variantOf(profileId, atom,
                normalized.source(), language, true));
        if (!normalized.english().isEmpty()) {
            variants.add(ProfileWriter.variantOf(profileId, atom,
                    normalized.english(), "en", false));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
