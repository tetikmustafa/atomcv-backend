package com.mustafatetik.atomcv.generation.repository;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.GenerationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private; reached through {@link GenerationRepository}. */
interface GenerationJpaRepository extends JpaRepository<Generation, UUID> {

    /**
     * The history a person has, newest first.
     *
     * <p>Superseded rows are left out. Faz G writes a new generation for every
     * hand edit and retires the one it replaced (Bolum 24.4), so twenty edits
     * of one CV is twenty-one rows and one of them is the CV. The rows stay --
     * nothing is deleted and every one of them can still be downloaded by id --
     * they are simply not what "my generations" means.
     */
    List<Generation> findByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(
            UUID userId, GenerationStatus status, Limit limit);

    /**
     * One anonymous session's generations, oldest first.
     *
     * <p>Both halves of the condition matter. The profile is what a session owns
     * and the null owner is what makes the row still anonymous, so a profile that
     * has just been adopted answers nothing here — which is what makes signing in
     * twice from one session harmless.
     */
    List<Generation> findByProfileIdAndUserIdIsNullOrderByCreatedAtAsc(UUID profileId);

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
              AND g.status <> :superseded
              AND (g.createdAt < :createdAt
                   OR (g.createdAt = :createdAt AND g.id < :id))
            ORDER BY g.createdAt DESC, g.id DESC
            """)
    List<Generation> findPageAfter(
            @Param("userId") UUID userId,
            @Param("superseded") GenerationStatus superseded,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            Limit limit);

    /**
     * Counted the same way the page is filtered, and it has to be.
     *
     * <p>A total that included the retired drafts would disagree with the list
     * printed under it -- "23 generations" over eleven rows and no next page.
     */
    long countByUserIdAndStatusNot(UUID userId, GenerationStatus status);

    /**
     * The generation that replaced this one, for an account (F-031).
     *
     * <p>The link is stored the other way round: an edit writes a new row
     * carrying the id of the one it retired, because that is the direction the
     * worker knows it in. A screen holding the retired id is in the other
     * position and has nowhere to go, so the edge is walked backwards here.
     *
     * <p><strong>First, not single.</strong> Two edits of one generation are
     * refused by {@code GENERATION_SUPERSEDED} once the first has landed, but
     * two in flight at once are both accepted -- the refusal reads a status the
     * worker has not written yet. That leaves a parent with two children, and
     * the newest is the one the person is looking at.
     */
    @Query("""
            SELECT g FROM Generation g
            WHERE g.userId = :userId AND g.parentGenerationId = :parentId
            ORDER BY g.createdAt DESC, g.id DESC
            """)
    List<Generation> findSuccessorOf(
            @Param("userId") UUID userId, @Param("parentId") UUID parentId, Limit limit);

    /**
     * The same edge for an anonymous session, under the two filters
     * {@code AnonymousGenerations} applies everywhere else: the row is this
     * session's profile's and still has no owner.
     */
    @Query("""
            SELECT g FROM Generation g
            WHERE g.profileId = :profileId AND g.userId IS NULL
              AND g.parentGenerationId = :parentId
            ORDER BY g.createdAt DESC, g.id DESC
            """)
    List<Generation> findAnonymousSuccessorOf(
            @Param("profileId") UUID profileId, @Param("parentId") UUID parentId, Limit limit);
}
