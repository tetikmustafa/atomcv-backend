package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.ProfileScopedRepository;
import java.util.List;
import java.util.Optional;
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

    /**
     * One atom's wording in one language, if it has one (Bolum 32.5).
     *
     * <p>What the pivot asks before it pays for a leg: an atom that already
     * has an English wording is translated from it, and nobody is charged for
     * producing a second one.
     */
    public Optional<AtomVariant> wordingOf(ProfileRef profile, UUID atomId, String language) {
        List<AtomVariant> wordings =
                jpa.findByProfileIdAndAtomIdAndLanguageOrderByIdAsc(profile.id(), atomId, language);
        // The neutral register first, because that is what a translation
        // produces and what a pivot should be translating from -- a formal
        // English wording would carry its register into the German.
        return wordings.stream()
                .filter(wording -> wording.getTone() == null)
                .findFirst()
                .or(() -> wordings.stream().findFirst());
    }

    /** The wordings that were translated from this one (Bolum 32.2). */
    public List<AtomVariant> derivedFrom(ProfileRef profile, UUID variantId) {
        return jpa.findByProfileIdAndDerivedFromVariantId(profile.id(), variantId);
    }
}
