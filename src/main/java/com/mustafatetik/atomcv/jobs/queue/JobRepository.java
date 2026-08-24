package com.mustafatetik.atomcv.jobs.queue;

import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.security.UserScopedRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The queue as a <em>user</em> sees it: their own jobs, and only theirs.
 *
 * <p>This is the half absolute rule 3 governs. Every read here takes the
 * acting user, so the progress stream and the status endpoint cannot be
 * pointed at a job id belonging to somebody else — which is the IDOR the SSE
 * endpoint of Bolum 30.6 would otherwise open, and the one place a job id is
 * handed to a browser.
 *
 * <p>{@link JobQueue} is the other half and is deliberately not scoped: a
 * worker has no acting user. The two are separate types so that neither can
 * be reached for by mistake.
 */
@Repository
public class JobRepository extends UserScopedRepository<Job> {

    private final JobJpaRepository jpa;

    JobRepository(JobJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<Job, UUID> delegate() {
        return jpa;
    }

    public List<Job> findAll(UserContext user) {
        return jpa.findByUserIdOrderByCreatedAtDesc(user.userId());
    }

    /**
     * Bolum 30.7: the same key from the same user is the same job.
     *
     * <p>Only ever asked for a signed-in user. The unique index behind it does
     * not cover anonymous rows — Postgres counts NULL owners as distinct — and
     * that defect is written down in EK D.6.5 rather than worked around here,
     * because whether the anonymous flow uses the queue at all is still open.
     */
    public Optional<Job> findByIdempotencyKey(UserContext user, String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return jpa.findByUserIdAndIdempotencyKey(user.userId(), key);
    }
}
