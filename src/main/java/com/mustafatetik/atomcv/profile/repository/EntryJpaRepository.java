package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.Entry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private; reached through {@link EntryRepository}. */
interface EntryJpaRepository extends JpaRepository<Entry, UUID> {

    List<Entry> findByProfileIdOrderByDisplayOrderAscIdAsc(UUID profileId);
}
