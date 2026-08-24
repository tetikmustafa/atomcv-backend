package com.mustafatetik.atomcv.jobs.queue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The queue as a worker sees it: claim, heartbeat, reclaim, release
 * (Bolum 30.2, 30.4).
 *
 * <p><strong>Deliberately not user-scoped, and separate from
 * {@code JobRepository} because of it.</strong> Absolute rule 3 exists to stop
 * a request from reaching another user's row; a worker is not a request and
 * has no acting user to scope by — it takes whatever is next in the queue.
 * Splitting the two makes that an explicit type rather than a scoped
 * repository with an unscoped method on it, which is the shape someone would
 * later call from a controller by accident.
 *
 * <p>SQL rather than JPA. {@code FOR UPDATE SKIP LOCKED} has no JPQL
 * equivalent, and the point of the claim is that it decides <em>inside one
 * statement</em>: two workers running it concurrently get different rows, or
 * one gets nothing. Reading and then writing would let both read the same row.
 */
@Repository
public class JobQueue {

    private static final Logger log = LoggerFactory.getLogger(JobQueue.class);

    /** Bolum 30.2, verbatim, minus the columns nothing reads back. */
    private static final String CLAIM = """
            UPDATE jobs SET
                status = 'running', locked_by = ?,
                locked_at = ?, heartbeat_at = ?, attempts = attempts + 1
            WHERE id = (
                SELECT id FROM jobs
                WHERE status = 'queued' AND run_after <= ?
                ORDER BY priority, created_at
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            RETURNING id
            """;

    private final JdbcTemplate jdbc;
    private final JobJpaRepository jpa;
    private final Clock clock;

    JobQueue(JdbcTemplate jdbc, JobJpaRepository jpa, Clock clock) {
        this.jdbc = jdbc;
        this.jpa = jpa;
        this.clock = clock;
    }

    /** Puts a job on the queue. Runnable immediately unless the caller said otherwise. */
    @Transactional
    public Job enqueue(Job job) {
        return jpa.save(job);
    }

    /**
     * The claimed row, as an entity.
     *
     * <p>Unscoped, and it is the reason this type is separate: the worker
     * loading what it just won has no acting user to check against.
     */
    public Optional<Job> find(UUID jobId) {
        return jpa.findById(jobId);
    }

    @Transactional
    public Job save(Job job) {
        return jpa.save(job);
    }

    /**
     * Take the next job, or nothing.
     *
     * @return the id of the job now held by {@code workerId}. The row itself
     *         is loaded afterwards through JPA — the claim is about winning
     *         it, not about reading it, and one statement doing both would mix
     *         two concerns in a query that must stay exactly as Bolum 30.2
     *         wrote it.
     */
    @Transactional
    public Optional<UUID> claim(String workerId) {
        Instant now = clock.instant();
        List<UUID> claimed = jdbc.query(CLAIM,
                (row, index) -> row.getObject(1, UUID.class),
                workerId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));
        return claimed.stream().findFirst();
    }

    /**
     * "Still working" (Bolum 30.4).
     *
     * <p>Scoped to the worker's own ids as well as to the ids given: a worker
     * that lost a job to the zombie collector must not go on refreshing
     * someone else's claim, which would keep a job alive under two owners.
     */
    @Transactional
    public int touchHeartbeat(String workerId, Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", jobIds.stream().map(id -> "?").toList());
        Object[] args = new Object[jobIds.size() + 2];
        args[0] = java.sql.Timestamp.from(clock.instant());
        args[1] = workerId;
        int index = 2;
        for (UUID id : jobIds) {
            args[index++] = id;
        }
        return jdbc.update(
                "UPDATE jobs SET heartbeat_at = ? WHERE locked_by = ? AND status = 'running'"
                        + " AND id IN (" + placeholders + ")",
                args);
    }

    /**
     * Jobs whose worker stopped saying anything, back to the queue
     * (Bolum 30.4).
     *
     * <p>The attempt is <em>not</em> given back. A worker that was killed
     * mid-generation may well have been killed by the generation, and a job
     * that reclaims itself forever is how one poisoned payload takes down a
     * queue. It gets the attempts its retry budget allows and no more.
     */
    @Transactional
    public int reclaimStale(Duration after) {
        Instant deadline = clock.instant().minus(after);
        int reclaimed = jdbc.update("""
                UPDATE jobs SET
                    status = 'queued', locked_by = NULL, locked_at = NULL, heartbeat_at = NULL
                WHERE status = 'running'
                  AND heartbeat_at < ?
                  AND attempts < max_attempts
                """, java.sql.Timestamp.from(deadline));

        // A job that ran out of attempts while its worker was dying cannot go
        // back to the queue, and leaving it 'running' forever would be a
        // spinner that never stops.
        int abandoned = jdbc.update("""
                UPDATE jobs SET
                    status = 'failed', locked_by = NULL, locked_at = NULL, heartbeat_at = NULL,
                    completed_at = ?, error = '{"code":"WORKER_LOST"}'::jsonb
                WHERE status = 'running'
                  AND heartbeat_at < ?
                  AND attempts >= max_attempts
                """, java.sql.Timestamp.from(clock.instant()),
                java.sql.Timestamp.from(deadline));

        if (reclaimed > 0 || abandoned > 0) {
            log.warn("Reclaimed {} stale jobs and abandoned {} out of attempts",
                    reclaimed, abandoned);
        }
        return reclaimed;
    }

    /**
     * Hand everything back on the way down (Bolum 30.4).
     *
     * <p>Without it a rolling deploy leaves every in-flight job locked until
     * the zombie collector notices, which is the stale timeout of dead time on
     * a screen someone is watching.
     */
    @Transactional
    public int releaseLocks(String workerId) {
        return jdbc.update("""
                UPDATE jobs SET
                    status = 'queued', locked_by = NULL, locked_at = NULL, heartbeat_at = NULL
                WHERE locked_by = ? AND status = 'running'
                """, workerId);
    }
}
