package com.mustafatetik.atomcv.rendering.repository;

import com.mustafatetik.atomcv.rendering.domain.SavedCustomization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private; reached through {@link SavedCustomizations}. */
interface SavedCustomizationJpaRepository extends JpaRepository<SavedCustomization, UUID> {

    List<SavedCustomization> findByProfileIdOrderByCreatedAtAsc(UUID profileId);

    /** Bolum 13's {@code UNIQUE (profile_id, name)}, asked before it is enforced. */
    Optional<SavedCustomization> findByProfileIdAndName(UUID profileId, String name);
}
