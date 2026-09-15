package com.mustafatetik.atomcv.rendering.repository;

import com.mustafatetik.atomcv.rendering.domain.SavedCustomization;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.ProfileScopedRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The named appearance settings of one profile (Bolum 33.2).
 *
 * <p>Scoped like everything else under a profile: a customization id that
 * belongs to somebody else reads as absent, never as forbidden (absolute
 * rule 3).
 */
@Repository
public class SavedCustomizations extends ProfileScopedRepository<SavedCustomization> {

    private final SavedCustomizationJpaRepository jpa;

    SavedCustomizations(SavedCustomizationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<SavedCustomization, UUID> delegate() {
        return jpa;
    }

    /** Oldest first, which is the order somebody made them in. */
    public List<SavedCustomization> findAll(ProfileRef profile) {
        return jpa.findByProfileIdOrderByCreatedAtAsc(profile.id());
    }

    public Optional<SavedCustomization> findByName(ProfileRef profile, String name) {
        return jpa.findByProfileIdAndName(profile.id(), name);
    }
}
