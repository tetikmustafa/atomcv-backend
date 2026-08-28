package com.mustafatetik.atomcv.generation.repository;

import com.mustafatetik.atomcv.generation.domain.SupportGrant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private, like every other Spring Data interface here. */
interface SupportGrantJpaRepository extends JpaRepository<SupportGrant, UUID> {

    Optional<SupportGrant> findFirstByGenerationIdAndUserIdOrderByGrantedAtDesc(
            UUID generationId, UUID userId);
}
