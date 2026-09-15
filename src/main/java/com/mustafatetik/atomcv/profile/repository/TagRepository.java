package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.AtomTag;
import com.mustafatetik.atomcv.profile.domain.TagSource;
import com.mustafatetik.atomcv.profile.domain.Tag;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.ProfileScopedRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** The tag vocabulary of one profile, and which atoms wear which of it. */
@Repository
public class TagRepository extends ProfileScopedRepository<Tag> {

    private final TagJpaRepository jpa;
    private final AtomTagJpaRepository links;

    TagRepository(TagJpaRepository jpa, AtomTagJpaRepository links) {
        this.jpa = jpa;
        this.links = links;
    }

    @Override
    protected JpaRepository<Tag, UUID> delegate() {
        return jpa;
    }

    public List<Tag> findAll(ProfileRef profile) {
        return jpa.findByProfileIdOrderByLabelAsc(profile.id());
    }

    /**
     * What Faz B reads: every tagged atom in the profile, with its labels.
     *
     * <p>One query for the whole profile rather than one per atom, and the
     * fifth query a generation makes — {@link
     * com.mustafatetik.atomcv.profile.service.ProfileAssembler} still loads
     * the tree in four. Tags are a scoring input, not part of what is
     * rendered, so general mode never pays for this one.
     *
     * <p>The query's order is kept all the way out. {@code Set.copyOf} and
     * {@code Map.copyOf} would <em>not</em> keep it: the JDK's immutable
     * collections iterate in an order salted per JVM run, so the same profile
     * came back in one order here and another on the CI runner — which is how
     * this was found. {@code UserFacingError} avoids the same trap for the
     * same reason.
     *
     * @return atoms that have at least one tag; an untagged atom is absent
     *         rather than present with an empty set, and the caller reads a
     *         missing key as "no tags"
     */
    public Map<UUID, Set<String>> labelsByAtom(ProfileRef profile) {
        Map<UUID, Set<String>> labels = new LinkedHashMap<>();
        for (AtomTagLabel row : jpa.findLabelsByProfileId(profile.id())) {
            labels.computeIfAbsent(row.atomId(), atom -> new LinkedHashSet<>())
                    .add(row.label());
        }
        labels.replaceAll((atom, set) -> Collections.unmodifiableSet(set));
        return Collections.unmodifiableMap(labels);
    }

    /**
     * Every tag on every atom of one profile, keyed by atom.
     *
     * <p>What {@link #labelsByAtom} is to Faz B, this is to the editor: one
     * query for the whole profile rather than one per atom, because the list
     * endpoint draws every atom at once, and the other shape is the most
     * likely performance mistake in this codebase.
     *
     * @return atoms that carry at least one tag; an untagged atom is absent
     *         rather than present with an empty list
     */
    public Map<UUID, List<AtomTagRow>> rowsByAtom(ProfileRef profile) {
        Map<UUID, List<AtomTagRow>> rows = new LinkedHashMap<>();
        for (AtomTagRow row : jpa.findRowsByProfileId(profile.id())) {
            rows.computeIfAbsent(row.atomId(), atom -> new ArrayList<>()).add(row);
        }
        rows.replaceAll((atom, list) -> Collections.unmodifiableList(list));
        return Collections.unmodifiableMap(rows);
    }

    /** The tags of one atom, in the same order the list endpoint uses. */
    public List<AtomTagRow> rowsOf(ProfileRef profile, UUID atomId) {
        return rowsByAtom(profile).getOrDefault(atomId, List.of());
    }

    /**
     * Puts a label on an atom, creating the profile's tag row if this is the
     * first atom to wear it.
     *
     * <p><strong>The label is canonicalised here and nowhere else.</strong>
     * {@code Tag.canonical} is the column's own rule, so a tag the extraction
     * wrote and a tag typed into the editor are one row rather than two that
     * never match each other.
     *
     * <p>Idempotent in both halves: a label the profile already knows reuses
     * its row, and an atom that already wears it keeps the link it has. The
     * source is not overwritten on a second attach — a person confirming a tag
     * the extraction guessed does not make it less of a guess, and the pair is
     * what the column records.
     *
     * <p>The caller has already resolved the atom through a scoped repository;
     * what this scopes is the tag, which is the row that carries the profile.
     */
    public AtomTagRow attach(ProfileRef profile, UUID atomId, String label, TagSource source) {
        String canonical = Tag.canonical(label);
        Tag tag = jpa.findByProfileIdAndLabel(profile.id(), canonical)
                .orElseGet(() -> jpa.save(new Tag(profile.id(), canonical)));

        AtomTag link = links.findByAtomIdAndTagId(atomId, tag.getId())
                .orElseGet(() -> links.save(new AtomTag(atomId, tag.getId(), source)));

        return new AtomTagRow(atomId, tag.getId(), tag.getLabel(), link.getSource());
    }

    /**
     * Takes a label off an atom.
     *
     * <p><strong>The tag row goes with the last atom wearing it.</strong> The
     * vocabulary is the profile's, and a label no atom carries is a suggestion
     * nobody made — it would sit in the tag list forever and grow with every
     * typo. The link rows are what the meaning is keyed on; the tag row is the
     * spelling.
     *
     * @return false when this profile has no such tag, or the atom was not
     *         wearing it. The endpoint turns that into a 404 rather than
     *         pretending a removal happened
     */
    public boolean detach(ProfileRef profile, UUID atomId, UUID tagId) {
        Optional<Tag> tag = findById(profile, tagId);
        if (tag.isEmpty()) {
            return false;
        }
        Optional<AtomTag> link = links.findByAtomIdAndTagId(atomId, tagId);
        if (link.isEmpty()) {
            return false;
        }
        links.delete(link.get());
        if (links.findByTagId(tagId).isEmpty()) {
            delete(profile, tag.get());
        }
        return true;
    }
}
