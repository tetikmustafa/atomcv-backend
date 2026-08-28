package com.mustafatetik.atomcv.generation.repository;

import com.mustafatetik.atomcv.generation.domain.GenerationFeedback;
import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.security.UserScopedRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * A user's own verdicts (Bolum 13).
 *
 * <p>Scoped like everything else (absolute rule 3). The finder takes a
 * generation id, which reaches a browser — so it takes the acting user with
 * it and answers nothing about anybody else's row.
 */
@Repository
public class FeedbackRepository extends UserScopedRepository<GenerationFeedback> {

    private final GenerationFeedbackJpaRepository jpa;

    FeedbackRepository(GenerationFeedbackJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<GenerationFeedback, UUID> delegate() {
        return jpa;
    }

    /** What this person already said about this generation, if anything. */
    public Optional<GenerationFeedback> findFor(UserContext user, UUID generationId) {
        return jpa.findByGenerationIdAndUserId(generationId, user.userId());
    }
}
