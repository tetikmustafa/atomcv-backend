package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.profile.repository.EntryRepository;
import com.mustafatetik.atomcv.profile.repository.SectionRepository;
import com.mustafatetik.atomcv.shared.security.CrossTenantAccessException;
import com.mustafatetik.atomcv.shared.security.ProfileOwned;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Loads a profile with four flat queries and joins them in memory
 * (Bolum 52.2).
 *
 * <p>The obvious alternative — associations and a {@code JOIN FETCH} chain —
 * produces a cartesian product across sections, entries, atoms and variants
 * and is slower than the four queries it replaces. The obvious mistake is
 * worse: touching a lazy collection per atom turns one load into hundreds of
 * queries, and nothing about the code that does it looks wrong.
 *
 * <p>{@link #assemble} is static and pure, so the joining can be tested
 * without a database, and the query count can be tested without the joining.
 */
@Service
public class ProfileAssembler {

    /**
     * Variants of one atom, in a fixed order: the primary first, then by
     * language, then by tone, then by id. Nothing downstream may depend on the
     * order rows happen to come back in.
     */
    private static final Comparator<AtomVariant> VARIANT_ORDER =
            Comparator.comparing(AtomVariant::isPrimary, Comparator.reverseOrder())
                    .thenComparing(AtomVariant::getLanguage)
                    .thenComparing(variant -> variant.getTone() == null ? "" : variant.getTone().name())
                    .thenComparing(variant -> variant.getId().toString());

    private final SectionRepository sections;
    private final EntryRepository entries;
    private final AtomRepository atoms;
    private final AtomVariantRepository variants;

    ProfileAssembler(SectionRepository sections, EntryRepository entries, AtomRepository atoms,
            AtomVariantRepository variants) {
        this.sections = sections;
        this.entries = entries;
        this.atoms = atoms;
        this.variants = variants;
    }

    public ProfileTree load(ProfileRef profile) {
        return assemble(
                profile.id(),
                sections.findAll(profile),
                entries.findAll(profile),
                atoms.findAll(profile),
                variants.findAll(profile));
    }

    /**
     * Joins four ordered lists into one tree.
     *
     * <p>Every row is checked against {@code profileId}. Four separate queries
     * mean four chances to pass the wrong scope, and a mixed result would be a
     * cross-tenant leak that looks like a rendering bug.
     *
     * @throws CrossTenantAccessException if any row belongs to another profile
     * @throws IllegalStateException      if a row points at a parent that is not in the input
     */
    public static ProfileTree assemble(UUID profileId, List<Section> sections, List<Entry> entries,
            List<Atom> atoms, List<AtomVariant> variants) {

        requireSameProfile(profileId, sections, "section");
        requireSameProfile(profileId, entries, "entry");
        requireSameProfile(profileId, atoms, "atom");
        requireSameProfile(profileId, variants, "atom variant");

        Map<UUID, List<AtomVariant>> variantsByAtom = new LinkedHashMap<>();
        Map<UUID, Atom> atomsById = new LinkedHashMap<>();
        for (Atom atom : atoms) {
            atomsById.put(atom.getId(), atom);
            variantsByAtom.put(atom.getId(), new ArrayList<>());
        }
        for (AtomVariant variant : variants) {
            List<AtomVariant> siblings = variantsByAtom.get(variant.getAtomId());
            if (siblings == null) {
                throw new IllegalStateException("A variant belongs to an atom outside the profile");
            }
            siblings.add(variant);
        }
        variantsByAtom.values().forEach(list -> list.sort(VARIANT_ORDER));

        Map<UUID, Entry> entriesById = new LinkedHashMap<>();
        Map<UUID, List<ProfileTree.AtomNode>> atomsByEntry = new LinkedHashMap<>();
        for (Entry entry : entries) {
            entriesById.put(entry.getId(), entry);
            atomsByEntry.put(entry.getId(), new ArrayList<>());
        }

        Map<UUID, List<ProfileTree.AtomNode>> sectionAtoms = new LinkedHashMap<>();
        Map<UUID, List<Entry>> entriesBySection = new LinkedHashMap<>();
        for (Section section : sections) {
            sectionAtoms.put(section.getId(), new ArrayList<>());
            entriesBySection.put(section.getId(), new ArrayList<>());
        }

        for (Entry entry : entries) {
            List<Entry> siblings = entriesBySection.get(entry.getSectionId());
            if (siblings == null) {
                throw new IllegalStateException("An entry belongs to a section outside the profile");
            }
            siblings.add(entry);
        }

        for (Atom atom : atoms) {
            var node = new ProfileTree.AtomNode(atom, variantsByAtom.get(atom.getId()));
            if (atom.getEntryId() == null) {
                List<ProfileTree.AtomNode> siblings = sectionAtoms.get(atom.getSectionId());
                if (siblings == null) {
                    throw new IllegalStateException("An atom belongs to a section outside the profile");
                }
                siblings.add(node);
            } else {
                List<ProfileTree.AtomNode> siblings = atomsByEntry.get(atom.getEntryId());
                if (siblings == null) {
                    throw new IllegalStateException("An atom belongs to an entry outside the profile");
                }
                Entry parent = entriesById.get(atom.getEntryId());
                if (!parent.getSectionId().equals(atom.getSectionId())) {
                    throw new IllegalStateException("An atom and its entry disagree about the section");
                }
                siblings.add(node);
            }
        }

        List<ProfileTree.SectionNode> tree = new ArrayList<>(sections.size());
        for (Section section : sections) {
            List<ProfileTree.EntryNode> entryNodes = new ArrayList<>();
            for (Entry entry : entriesBySection.get(section.getId())) {
                entryNodes.add(new ProfileTree.EntryNode(entry, atomsByEntry.get(entry.getId())));
            }
            tree.add(new ProfileTree.SectionNode(section, entryNodes, sectionAtoms.get(section.getId())));
        }
        return new ProfileTree(profileId, tree);
    }

    private static void requireSameProfile(UUID profileId, List<? extends ProfileOwned> rows, String what) {
        for (ProfileOwned row : rows) {
            if (!profileId.equals(row.getProfileId())) {
                throw new CrossTenantAccessException("A " + what + " belongs to a different profile");
            }
        }
    }
}
