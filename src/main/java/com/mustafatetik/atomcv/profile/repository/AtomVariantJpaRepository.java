package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Package-private; reached through {@link AtomVariantRepository}.
 *
 * <p>Loading by {@code profile_id} rather than by atom is the whole point of
 * that denormalized column (Bolum 52.2): one flat query instead of one per
 * atom.
 */
interface AtomVariantJpaRepository extends JpaRepository<AtomVariant, UUID> {

    List<AtomVariant> findByProfileIdOrderByIdAsc(UUID profileId);
}
