package com.mustafatetik.atomcv.profile.seed;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import java.util.List;

/**
 * One fixture, as rows.
 *
 * <p>Flat lists as well as the tree, because the two consumers want different
 * things: the golden tests want a {@link ProfileTree} and no database, and the
 * seeder wants rows to persist in insertion order.
 */
public record GoldenProfile(
        String name,
        String description,
        Profile profile,
        List<Section> sections,
        List<Entry> entries,
        List<Atom> atoms,
        List<AtomVariant> variants,
        ProfileTree tree) {

    public GoldenProfile {
        sections = List.copyOf(sections);
        entries = List.copyOf(entries);
        atoms = List.copyOf(atoms);
        variants = List.copyOf(variants);
    }

    /** Shape only: a fixture is written to look like a real person's CV. */
    @Override
    public String toString() {
        return "GoldenProfile[" + name + ", sections=" + sections.size()
                + ", entries=" + entries.size() + ", atoms=" + atoms.size() + "]";
    }
}
