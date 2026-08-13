package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.ProfileScopedRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Entries of one profile, across all of its sections. */
@Repository
public class EntryRepository extends ProfileScopedRepository<Entry> {

    private final EntryJpaRepository jpa;

    EntryRepository(EntryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<Entry, UUID> delegate() {
        return jpa;
    }

    public List<Entry> findAll(ProfileRef profile) {
        return jpa.findByProfileIdOrderByDisplayOrderAscIdAsc(profile.id());
    }
}
