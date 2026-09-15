package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.AtomTag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private; reached through {@link TagRepository}. */
interface AtomTagJpaRepository extends JpaRepository<AtomTag, AtomTag.Key> {

    Optional<AtomTag> findByAtomIdAndTagId(UUID atomId, UUID tagId);

    List<AtomTag> findByTagId(UUID tagId);
}
