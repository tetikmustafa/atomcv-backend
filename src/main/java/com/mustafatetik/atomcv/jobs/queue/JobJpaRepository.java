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

    Optional<Job> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
