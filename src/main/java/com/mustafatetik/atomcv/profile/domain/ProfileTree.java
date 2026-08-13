package com.mustafatetik.atomcv.profile.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A whole profile in memory: sections, their entries, their atoms and every
 * wording of each atom.
 *
 * <p>This is what the four flat queries add up to (Bolum 52.2). The entities
 * themselves hold no associations, so the shape a caller wants exists only
 * here — which keeps "how a profile is loaded" one decision in one place
 * rather than a lazy proxy anyone can trip over.
 */
public record ProfileTree(UUID profileId, List<SectionNode> sections) {

    public ProfileTree {
        sections = List.copyOf(sections);
    }

    /** A section with the entries and the section-level atoms hanging off it. */
    public record SectionNode(Section section, List<EntryNode> entries, List<AtomNode> atoms) {

        public SectionNode {
            entries = List.copyOf(entries);
            atoms = List.copyOf(atoms);
        }
    }

    /** One position, degree or project, with its atoms. */
    public record EntryNode(Entry entry, List<AtomNode> atoms) {

        public EntryNode {
            atoms = List.copyOf(atoms);
        }
    }

    /** One fact, with every wording of it. */
    public record AtomNode(Atom atom, List<AtomVariant> variants) {

        public AtomNode {
            variants = List.copyOf(variants);
        }

        /** The wording used when nothing more specific is asked for. */
        public Optional<AtomVariant> primaryVariant() {
            return variants.stream().filter(AtomVariant::isPrimary).findFirst();
        }

        public Optional<AtomVariant> variantIn(String language) {
            return variants.stream()
                    .filter(variant -> variant.getLanguage().equals(language))
                    .findFirst();
        }
    }

    public int atomCount() {
        return sections.stream()
                .mapToInt(section -> section.atoms().size()
                        + section.entries().stream().mapToInt(entry -> entry.atoms().size()).sum())
                .sum();
    }
}
