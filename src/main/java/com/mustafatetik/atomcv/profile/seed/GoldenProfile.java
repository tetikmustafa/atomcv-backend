package com.mustafatetik.atomcv.profile.seed;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
        /**
         * What Faz B is handed as {@code tagsByAtom} (Bolum 19.1, Bolum 52.2).
         *
         * <p>Beside the rows rather than inside them, because that is where it
         * lives in production: a tag is a row in {@code tags} joined through
         * {@code atom_tags}, and scoring takes it as a map rather than reading
         * it off the tree. A fixture that kept tags on the atom would have
         * been a shape the scorer never sees.
         *
         * <p>Insertion order, and for the reason CLAUDE.md gives: {@code
         * Map.copyOf} iterates in an order salted per JVM run, and a score
         * built from it would move between runs.
         */
        Map<UUID, Set<String>> tagsByAtom,
        ProfileTree tree) {

    public GoldenProfile {
        sections = List.copyOf(sections);
        entries = List.copyOf(entries);
        atoms = List.copyOf(atoms);
        variants = List.copyOf(variants);
        tagsByAtom = tagsByAtom == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(tagsByAtom));
        // The same type TagRepository.labelsByAtom answers with, down to the
        // ordered set: the scorer takes one of these either way, and a fixture
        // shaped differently from the query would be a fixture of a different
        // system.

    }

    /** Shape only: a fixture is written to look like a real person's CV. */
    @Override
    public String toString() {
        return "GoldenProfile[" + name + ", sections=" + sections.size()
                + ", entries=" + entries.size() + ", atoms=" + atoms.size() + "]";
    }
}
