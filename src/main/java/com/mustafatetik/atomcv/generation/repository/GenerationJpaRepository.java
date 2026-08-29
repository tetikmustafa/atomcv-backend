package com.mustafatetik.atomcv.generation.repository;

import com.mustafatetik.atomcv.generation.domain.Generation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private; reached through {@link GenerationRepository}. */
interface GenerationJpaRepository extends JpaRepository<Generation, UUID> {

    List<Generation> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Limit limit);

    /**
     * The page after a cursor, keyset rather than offset (EK D.8.7).
     *
     * <p>Written out rather than derived because the condition is a comparison
     * of the whole sort key against the whole cursor, and no method name spells
     * that. Both halves matter: rows sharing a {@code created_at} are ordered
     * by id, so resuming on the timestamp alone would skip the rest of a
     * tied group or repeat it.
     */
    @Query("""
            SELECT g FROM Generation g
            WHERE g.userId = :userId
              AND (g.createdAt < :createdAt
                   OR (g.createdAt = :createdAt AND g.id < :id))
            ORDER BY g.createdAt DESC, g.id DESC
            """)
    List<Generation> findPageAfter(
            @Param("userId") UUID userId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            Limit limit);

    long countByUserId(UUID userId);
}
