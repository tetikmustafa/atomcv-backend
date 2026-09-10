package com.mustafatetik.atomcv.jobs.queue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Package-private; reached through {@link JobRepository} for anything a user
 * asked for and through {@link JobQueue} for anything a worker did.
 */
interface JobJpaRepository extends JpaRepository<Job, UUID> {

    List<Job> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * An ownerless job under this key that has not finished.
     *
     * <p>For the work nobody owns — a capacity measurement belongs to a
     * geometry rather than to a person — where the scoped repository has no
     * owner to be given.
     */
    boolean existsByIdempotencyKeyAndUserIdIsNullAndStatusIn(
            String idempotencyKey, java.util.Collection<JobStatus> statuses);

    Optional<Job> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /** The same two, for a caller who has not signed up (Adim 3.6). */
    List<Job> findByAnonSessionIdOrderByCreatedAtDesc(String anonSessionId);

    Optional<Job> findByAnonSessionIdAndIdempotencyKey(
            String anonSessionId, String idempotencyKey);
}
