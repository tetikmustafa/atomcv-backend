package com.mustafatetik.atomcv.generation.repository;

import com.mustafatetik.atomcv.generation.domain.GenerationFeedback;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private, like every other Spring Data interface here. */
interface GenerationFeedbackJpaRepository extends JpaRepository<GenerationFeedback, UUID> {

    Optional<GenerationFeedback> findByGenerationIdAndUserId(UUID generationId, UUID userId);
}
