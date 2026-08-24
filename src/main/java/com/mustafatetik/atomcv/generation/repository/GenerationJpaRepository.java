package com.mustafatetik.atomcv.generation.repository;

import com.mustafatetik.atomcv.generation.domain.Generation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private; reached through {@link GenerationRepository}. */
interface GenerationJpaRepository extends JpaRepository<Generation, UUID> {

    List<Generation> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Limit limit);
}
