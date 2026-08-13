package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.ProfileScopedRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Atoms of one profile, both entry-level and section-level. */
@Repository
public class AtomRepository extends ProfileScopedRepository<Atom> {

    private final AtomJpaRepository jpa;

    AtomRepository(AtomJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<Atom, UUID> delegate() {
        return jpa;
    }

    public List<Atom> findAll(ProfileRef profile) {
        return jpa.findByProfileIdOrderByDisplayOrderAscIdAsc(profile.id());
    }
}
