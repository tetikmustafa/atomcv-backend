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
 * that denormalized column (Bolum 52.2): one flat query instead of one per
 * atom.
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
     * Every wording derived from one that has just changed (Bolum 32.2).
     *
     * <p>Loaded rather than bulk-updated, and that is the difference between
     * this and {@link #clearPrimary}. Bolum 32.2 does two things with these
     * rows and only the first is the same for all of them: they all go stale,
     * but a wording the user wrote themselves must not be queued for
     * regeneration. A statement cannot answer "which ones were queued", and
     * the caller has to know.
     */
    List<AtomVariant> findByProfileIdAndDerivedFromVariantId(
            UUID profileId, UUID derivedFromVariantId);
}
