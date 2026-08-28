package com.mustafatetik.atomcv.generation.repository;

import com.mustafatetik.atomcv.generation.domain.SupportGrant;
import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.security.UserScopedRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Permission to read a CV, and the record of what was done with it
 * (Bolum 48.4).
 *
 * <p>Scoped like everything else, and here the rule earns its keep twice
 * over: this table is the one door through absolute rule 4, so a read that
 * was not the owner's own would be the product handing somebody else's
 * document to a caller who asked nicely.
 */
@Repository
public class SupportGrantRepository extends UserScopedRepository<SupportGrant> {

    private final SupportGrantJpaRepository jpa;

    SupportGrantRepository(SupportGrantJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<SupportGrant, UUID> delegate() {
        return jpa;
    }

    /**
     * The most recent grant on this generation, open or not.
     *
     * <p>Newest rather than open: a revoked or expired grant is what the
     * person is shown when they ask what became of their permission, and an
     * accessed one is the audit trail Bolum 48.4 promises them.
     */
    public Optional<SupportGrant> findFor(UserContext user, UUID generationId) {
        return jpa.findFirstByGenerationIdAndUserIdOrderByGrantedAtDesc(
                generationId, user.userId());
    }
}
