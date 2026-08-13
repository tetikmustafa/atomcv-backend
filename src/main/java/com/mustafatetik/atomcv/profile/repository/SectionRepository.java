package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.ProfileScopedRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Sections of one profile. Every method takes the scope it reads within. */
@Repository
public class SectionRepository extends ProfileScopedRepository<Section> {

    private final SectionJpaRepository jpa;

    SectionRepository(SectionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<Section, UUID> delegate() {
        return jpa;
    }

    public List<Section> findAll(ProfileRef profile) {
        return jpa.findByProfileIdOrderByDisplayOrderAscIdAsc(profile.id());
    }
}
