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

    /**
     * Every job this caller started (Adim 3.6).
     *
     * <p>Owner rather than user, and that is the whole of the change: the
     * queue always had a column for an anonymous session and no way to say
     * "this caller" without meaning an account, so somebody who had not signed
     * up could not poll the job they had just started.
     */
    public List<Job> findAll(JobOwner owner) {
        return owner.isAnonymous()
                ? jpa.findByAnonSessionIdOrderByCreatedAtDesc(owner.anonSessionId())
                : jpa.findByUserIdOrderByCreatedAtDesc(owner.userId());
    }

    /**
     * One job, if it belongs to this caller.
     *
     * <p>Empty rather than an exception when it belongs to somebody else: the
     * endpoint answers 404 either way, and telling a caller that a job exists
     * but is not theirs is telling them something about a stranger.
     */
    public Optional<Job> findById(JobOwner owner, UUID jobId) {
        return delegate().findById(jobId).filter(job -> belongsTo(job, owner));
    }

    /** Bolum 30.7: the same key from the same caller is the same job. */
    public Optional<Job> findByIdempotencyKey(JobOwner owner, String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return owner.isAnonymous()
                ? jpa.findByAnonSessionIdAndIdempotencyKey(owner.anonSessionId(), key)
                : jpa.findByUserIdAndIdempotencyKey(owner.userId(), key);
    }

    /**
     * <p>V3 is what makes the anonymous half of this actually hold. V1's
     * unique index was {@code (user_id, idempotency_key)}, and an anonymous
     * row has a null owner — which Postgres counts as distinct from every
     * other null, so the double click Bolum 30.7 absorbs went through twice.
     */
    private static boolean belongsTo(Job job, JobOwner owner) {
        return owner.isAnonymous()
                ? owner.anonSessionId().equals(job.getAnonSessionId())
                : owner.userId().equals(job.getOwnerId());
    }

}
