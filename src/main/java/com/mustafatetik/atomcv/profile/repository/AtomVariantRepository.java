package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.ProfileScopedRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Every wording of every atom in one profile, in one query. */
@Repository
public class AtomVariantRepository extends ProfileScopedRepository<AtomVariant> {

    private final AtomVariantJpaRepository jpa;

    AtomVariantRepository(AtomVariantJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<AtomVariant, UUID> delegate() {
        return jpa;
    }

    public List<AtomVariant> findAll(ProfileRef profile) {
        return jpa.findByProfileIdOrderByIdAsc(profile.id());
    }

    /** Demotes whatever is primary on this atom, within this profile only. */
    public void clearPrimary(ProfileRef profile, UUID atomId) {
        jpa.clearPrimary(profile.id(), atomId);
    }

    /** The wordings that were translated from this one (Bolum 32.2). */
    public List<AtomVariant> derivedFrom(ProfileRef profile, UUID variantId) {
        return jpa.findByProfileIdAndDerivedFromVariantId(profile.id(), variantId);
    }
}
