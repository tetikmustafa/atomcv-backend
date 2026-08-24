package com.mustafatetik.atomcv.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * One atom wearing one tag (Bolum 13).
 *
 * <p>An entity rather than an {@code @ElementCollection} on {@link Atom},
 * for the same reason the rest of the profile has none: a collection is loaded
 * lazily per atom, and Faz B reads the tags of every atom in a profile at
 * once. As a row it is one query for the whole profile
 * ({@code TagRepository.labelsByAtom}); as a collection it is one per atom,
 * and nothing about the code that does that looks wrong (Bolum 52.2).
 *
 * <p><strong>It carries no {@code profile_id}.</strong> The table is keyed on
 * the two ids alone, so this is the one profile-owned thing that cannot
 * implement {@link com.mustafatetik.atomcv.shared.security.ProfileOwned}, and
 * scoping it means joining to {@link Tag} — which is exactly what the
 * repository does and the only way it is ever read.
 */
@Entity
@Table(name = "atom_tags")
@IdClass(AtomTag.Key.class)
public class AtomTag {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID atomId;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID tagId;

    @Convert(converter = TagSource.JpaConverter.class)
    @Column(nullable = false)
    private TagSource source = TagSource.AUTO;

    protected AtomTag() {
        // JPA
    }

    public AtomTag(UUID atomId, UUID tagId, TagSource source) {
        this.atomId = Objects.requireNonNull(atomId, "atomId");
        this.tagId = Objects.requireNonNull(tagId, "tagId");
        this.source = Objects.requireNonNull(source, "source");
    }

    public UUID getAtomId() {
        return atomId;
    }

    public UUID getTagId() {
        return tagId;
    }

    public TagSource getSource() {
        return source;
    }

    public void setSource(TagSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AtomTag tag
                && atomId.equals(tag.atomId) && tagId.equals(tag.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(atomId, tagId);
    }

    @Override
    public String toString() {
        return "AtomTag[" + atomId + ", " + tagId + ", " + source + "]";
    }

    /**
     * The composite primary key, {@code (atom_id, tag_id)}.
     *
     * <p>A class and not a record, which is the one place this codebase does
     * not prefer one: an {@code @IdClass} is instantiated reflectively through
     * a no-arg constructor and its fields are written afterwards, and a
     * record's fields are final.
     */
    public static class Key implements Serializable {

        private UUID atomId;
        private UUID tagId;

        public Key() {
            // JPA
        }

        public Key(UUID atomId, UUID tagId) {
            this.atomId = atomId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(atomId, key.atomId) && Objects.equals(tagId, key.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(atomId, tagId);
        }
    }
}
