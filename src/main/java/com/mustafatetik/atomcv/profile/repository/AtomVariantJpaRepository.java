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
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AtomVariant variant set variant.isPrimary = false
            where variant.atomId = :atomId and variant.profileId = :profileId
            """)
    int clearPrimary(UUID profileId, UUID atomId);
}
