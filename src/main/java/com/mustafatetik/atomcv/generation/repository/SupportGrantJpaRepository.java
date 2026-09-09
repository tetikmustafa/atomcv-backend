package com.mustafatetik.atomcv.generation.repository;

import com.mustafatetik.atomcv.generation.domain.SupportGrant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private, like every other Spring Data interface here. */
interface SupportGrantJpaRepository extends JpaRepository<SupportGrant, UUID> {

    Optional<SupportGrant> findFirstByGenerationIdAndUserIdOrderByGrantedAtDesc(
            UUID generationId, UUID userId);

    /**
     * Without the owner, for {@link SupportGrantLookup} only — the grant is what
     * says who the owner is, so the reader cannot name one before reading it.
     */
    Optional<SupportGrant> findFirstByGenerationIdOrderByGrantedAtDesc(UUID generationId);
}
