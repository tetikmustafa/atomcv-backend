package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.Atom;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private; reached through {@link AtomRepository}. */
interface AtomJpaRepository extends JpaRepository<Atom, UUID> {

    List<Atom> findByProfileIdOrderByDisplayOrderAscIdAsc(UUID profileId);
}
