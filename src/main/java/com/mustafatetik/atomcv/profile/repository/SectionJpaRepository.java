package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.Section;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Package-private on purpose: everything outside this package goes through
 * {@link SectionRepository}, which cannot be called without a
 * {@code ProfileRef}.
 *
 * <p>The id is part of the ordering so that two rows sharing a display order
 * still come back in the same sequence every time. Selection is required to be
 * deterministic, and it cannot be if its input is not.
 */
interface SectionJpaRepository extends JpaRepository<Section, UUID> {

    List<Section> findByProfileIdOrderByDisplayOrderAscIdAsc(UUID profileId);
}
