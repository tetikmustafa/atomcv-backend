package com.mustafatetik.atomcv.generation.selection;

import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything selection needs, and nothing it does not (Bolum 20).
 *
 * <p>No text: what a bullet says does not change what it costs or what it
 * scores, and both of those are already here. Selection works on numbers,
 * which is what makes it deterministic and testable without a database.
 */
public record SelectionRequest(List<SectionPlan> sections, int maxPages, CapacityModel capacity) {

    public SelectionRequest {
        sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        Objects.requireNonNull(capacity, "capacity");
        if (maxPages < 1) {
            throw new IllegalArgumentException("A CV has at least one page");
        }
    }

    /** A heading, its entries, and any atoms hanging straight off it. */
    public record SectionPlan(
            UUID sectionId,
            boolean alwaysInclude,
            List<EntryPlan> entries,
            List<AtomCandidate> atoms) {

        public SectionPlan {
            entries = List.copyOf(entries);
            atoms = List.copyOf(atoms);
            // An atom hanging off the section carries no entry. One that
            // claimed an entry here would be charged for a heading that is
            // never printed, and the page budget would be wrong by that much.
            atoms.forEach(atom -> {
                if (atom.entryId() != null) {
                    throw new IllegalArgumentException(
                            "A section-level atom belongs to no entry");
                }
            });
        }

        public boolean isEmpty() {
            return atoms.isEmpty() && entries.stream().allMatch(entry -> entry.atoms().isEmpty());
        }
    }

    /**
     * @param minAtoms below this many bullets the entry is not worth printing,
     *                 so selection either keeps this many or drops it whole
     *                 (Bolum 20.2, constraint 4)
     */
    public record EntryPlan(UUID entryId, short minAtoms, List<AtomCandidate> atoms) {

        public EntryPlan {
            atoms = List.copyOf(atoms);
            if (minAtoms < 0) {
                throw new IllegalArgumentException("minAtoms is not negative");
            }
            // The atom has to agree about which entry it is in. When it does
            // not, selection charges nothing for the entry heading and the
            // budget silently gains 22.76 points per entry — a page that
            // overflows for no visible reason (EK D.8.5).
            UUID owner = entryId;
            atoms.forEach(atom -> {
                if (!owner.equals(atom.entryId())) {
                    throw new IllegalArgumentException(
                            "An atom listed under an entry has to carry its id");
                }
            });
        }
    }

    /**
     * One atom, as a number.
     *
     * @param renderCostPt what it occupies, measured or estimated
     * @param alwaysInclude the user's lock: it goes in whatever the score says
     */
    public record AtomCandidate(
            UUID atomId,
            UUID variantId,
            UUID entryId,
            double score,
            double renderCostPt,
            boolean alwaysInclude,
            boolean active) {

        public AtomCandidate {
            Objects.requireNonNull(atomId, "atomId");
            if (renderCostPt <= 0) {
                throw new IllegalArgumentException("An atom occupies some space");
            }
            if (score < 0 || score > 1) {
                throw new IllegalArgumentException("A score is between 0 and 1, was " + score);
            }
        }
    }
}
