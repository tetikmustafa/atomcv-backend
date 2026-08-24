package com.mustafatetik.atomcv.generation.repository;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.security.UserScopedRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * A user's own generations (Bolum 41.2).
 *
 * <p>The generation id reaches a browser twice — in the job's terminal event
 * and in the download link — so every read here is scoped. Absolute rule 3.
 */
@Repository
public class GenerationRepository extends UserScopedRepository<Generation> {

    private final GenerationJpaRepository jpa;

    GenerationRepository(GenerationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<Generation, UUID> delegate() {
        return jpa;
    }

    /**
     * Newest first, ties broken by id.
     *
     * <p>Two generations of the same profile a second apart is ordinary — Faz
     * G's edit loop does exactly that — and {@code created_at} alone leaves
     * their order to the database. EK D.8.7's cursor pagination arrives with
     * the listing endpoint; the limit is here so nothing accidentally loads a
     * year of history to show ten rows.
     */
    public List<Generation> findRecent(UserContext user, int limit) {
        return jpa.findByUserIdOrderByCreatedAtDescIdDesc(user.userId(), Limit.of(limit));
    }
}
