package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Package-private; reached through {@link AtomVariantRepository}.
 *
 * <p>Loading by {@code profile_id} rather than by atom is the whole point of
 * that denormalized column: one flat query instead of one per atom.
 */
interface AtomVariantJpaRepository extends JpaRepository<AtomVariant, UUID> {

    List<AtomVariant> findByProfileIdOrderByIdAsc(UUID profileId);

    /**
     * Clears the primary flag on an atom's wordings in one statement.
     *
     * <p>A unique index allows one primary per atom, so promoting a wording
     * has to demote the other before the new flag reaches the database.
     * Leaving both writes to the persistence context would let Hibernate order
     * them the other way round and trip the index.
     *
     * <p>{@code update versioned} because a bulk update otherwise walks past
     * {@code @Version}, and the demoted row would keep the etag it had before
     * it changed — a client holding that etag could then overwrite a demotion
     * it never saw, which is the one thing the optimistic lock exists to
     * prevent (F-001).
     *
     * <p>The {@code isPrimary = true} filter narrows the statement to the row
     * that actually changes. Without it every wording of the atom takes a
     * version bump for a write that did not touch it, including the one being
     * promoted — whose merge would then fail on a version it did not know it
     * had.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update versioned AtomVariant variant set variant.isPrimary = false
            where variant.atomId = :atomId and variant.profileId = :profileId
              and variant.isPrimary = true
            """)
    int clearPrimary(UUID profileId, UUID atomId);

    /**
     * Every wording derived from one that has just changed.
     *
     * <p>Loaded rather than bulk-updated, and that is the difference between
     * this and {@link #clearPrimary}. Two things happen to these rows and only
     * the first is the same for all of them: they all go stale, but a wording
     * the user wrote themselves must not be queued for regeneration. A
     * statement cannot answer "which ones were queued", and the caller has to
     * know.
     */
    List<AtomVariant> findByProfileIdAndDerivedFromVariantId(
            UUID profileId, UUID derivedFromVariantId);

    /**
     * One atom's wordings in one language (the pivot asks for these by name).
     *
     * <p><strong>A list, not one row.</strong> The unique index is on
     * {@code (atom_id, language, tone)}, so an atom that has both a neutral
     * English wording and a formal one is two rows and a single-result query
     * would throw on exactly the profile that had done the most work. The
     * caller picks.
     *
     * <p>By atom rather than through the profile-wide load, because the caller
     * is one translation among sixty running at once: the flat query is the
     * right shape for assembling a tree and the wrong shape for asking about
     * a single atom sixty times.
     */
    List<AtomVariant> findByProfileIdAndAtomIdAndLanguageOrderByIdAsc(
            UUID profileId, UUID atomId, String language);
}
