package com.mustafatetik.atomcv.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobOwner;
import com.mustafatetik.atomcv.jobs.queue.JobRepository;
import com.mustafatetik.atomcv.jobs.queue.JobStatus;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The queue against a real Postgres (Bolum 30.2, 30.4).
 *
 * <p><strong>Not {@code @Transactional}.</strong> The property this class
 * exists to prove is what two concurrent transactions do to one row, and a
 * test that wrapped everything in a third would prove the opposite of what it
 * looked like it proved: with the rows uncommitted, the second claimant sees
 * an empty queue and passes for the wrong reason. Rows are deleted between
 * tests instead.
 */
class JobQueueIT extends AbstractIntegrationTest {

    @Autowired
    private JobQueue queue;

    @Autowired
    private JobRepository jobs;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Autowired
    private DataSource dataSource;

    private UUID userId;

    @BeforeEach
    void startFromAnEmptyQueue() {
        jdbc.update("DELETE FROM jobs");
        userId = jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
    }

    // ── Bolum 30.2: taking a job ─────────────────────────────────────────

    /**
     * Two workers asking at the same instant get different rows or nothing —
     * never the same row, which would render and charge for the same
     * generation twice.
     *
     * <p>A barrier rather than a sleep: both threads are held until both are
     * ready, so the statements really do overlap.
     *
     * <p><strong>This does not test {@code SKIP LOCKED}, and it was written
     * believing it did.</strong> Removing those two words leaves it passing:
     * under READ COMMITTED a plain {@code FOR UPDATE} blocks on the held row,
     * then re-evaluates the qualifier once the lock clears, finds the row no
     * longer {@code queued}, and moves to the next one. No duplicate either
     * way. What {@code SKIP LOCKED} actually buys is measured by
     * {@link #aworkerNeverWaitsBehindAnotherWorkersLock}.
     */
    @Test
    void twoWorkersAskingAtOnceNeverGetTheSameJob() throws Exception {
        for (int index = 0; index < 8; index++) {
            enqueue(JobType.GENERATION);
        }

        int workers = 4;
        var barrier = new CyclicBarrier(workers);
        var pool = Executors.newFixedThreadPool(workers);
        try {
            List<Callable<List<UUID>>> races = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                String workerId = "worker-" + worker;
                races.add(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    List<UUID> mine = new ArrayList<>();
                    for (int attempt = 0; attempt < 2; attempt++) {
                        queue.claim(workerId).ifPresent(mine::add);
                    }
                    return mine;
                });
            }

            List<UUID> claimed = new ArrayList<>();
            for (Future<List<UUID>> race : pool.invokeAll(races)) {
                claimed.addAll(race.get());
            }

            assertThat(claimed).hasSize(8);
            assertThat(Set.copyOf(claimed)).hasSize(8);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * What {@code SKIP LOCKED} is actually for: a worker never waits behind
     * another worker's transaction.
     *
     * <p>One job in the queue, and a second connection holding a row lock on
     * it without committing. The claim must come back empty <em>at once</em> —
     * there is nothing else to take. Without {@code SKIP LOCKED} the statement
     * blocks until the holder commits, the future times out, and this fails.
     * That is the whole difference, and it is a liveness property rather than
     * a correctness one: a queue where every idle poll can park behind one
     * slow generation stops being a queue.
     */
    @Test
    void aworkerNeverWaitsBehindAnotherWorkersLock() throws Exception {
        enqueue(JobType.GENERATION);

        var pool = Executors.newSingleThreadExecutor();
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(
                    "SELECT id FROM jobs WHERE status = 'queued' FOR UPDATE")) {
                lock.executeQuery();
            }

            Future<Optional<UUID>> claimed = pool.submit(() -> queue.claim("worker-2"));

            assertThat(claimed.get(5, TimeUnit.SECONDS)).isEmpty();
            holder.rollback();
        } finally {
            pool.shutdownNow();
        }
    }

    /** Bolum 30.3: the user waiting on a screen goes before the background work. */
    @Test
    void thelowestPriorityNumberIsTakenFirst() {
        enqueue(JobType.MEASUREMENT);
        enqueue(JobType.EMBEDDING);
        enqueue(JobType.GENERATION);

        UUID first = queue.claim("worker").orElseThrow();

        assertThat(queue.find(first).orElseThrow().getType()).isEqualTo(JobType.GENERATION);
    }

    /** Bolum 30.5's backoff is only a backoff if the claim honours it. */
    @Test
    void ajobWaitingOutItsBackoffIsNotTaken() {
        var waiting = new Job(JobType.GENERATION, userId, Map.of(),
                clock.instant().plus(Duration.ofMinutes(10)));
        queue.enqueue(waiting);

        assertThat(queue.claim("worker")).isEmpty();
    }

    @Test
    void claimingMarksTheRowRunningAndCountsTheAttempt() {
        Job queued = enqueue(JobType.GENERATION);

        UUID claimed = queue.claim("worker-1").orElseThrow();

        assertThat(claimed).isEqualTo(queued.getId());
        Job running = queue.find(claimed).orElseThrow();
        assertThat(running.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(running.getAttempts()).isEqualTo((short) 1);
        assertThat(running.getLockedBy()).isEqualTo("worker-1");
        assertThat(running.getLockedAt()).isNotNull();
        assertThat(running.getHeartbeatAt()).isNotNull();
    }

    @Test
    void anemptyQueueGivesNothingRatherThanFailing() {
        assertThat(queue.claim("worker")).isEmpty();
    }

    // ── Bolum 30.4: durability ───────────────────────────────────────────

    /** A worker that stopped answering has its job taken away from it. */
    @Test
    void asilentWorkersJobGoesBackToTheQueue() {
        UUID claimed = enqueueAndClaim("worker-1");
        silentSince(claimed, Duration.ofMinutes(10));

        assertThat(queue.reclaimStale(Duration.ofMinutes(2))).isEqualTo(1);

        Job reclaimed = queue.find(claimed).orElseThrow();
        assertThat(reclaimed.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(reclaimed.getLockedBy()).isNull();
        // The attempt is not given back: a worker killed mid-generation may
        // well have been killed by the generation.
        assertThat(reclaimed.getAttempts()).isEqualTo((short) 1);
    }

    /** A worker that is still talking keeps what it has. */
    @Test
    void aliveWorkersJobIsLeftAlone() {
        UUID claimed = enqueueAndClaim("worker-1");

        assertThat(queue.reclaimStale(Duration.ofMinutes(2))).isZero();
        assertThat(queue.find(claimed).orElseThrow().getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    /**
     * A job that ran out of attempts while its worker was dying cannot go back
     * to the queue. Leaving it {@code running} would be a spinner that never
     * stops on a screen someone is watching.
     */
    @Test
    void ajobOutOfAttemptsIsFailedRatherThanReclaimedForever() {
        UUID claimed = enqueueAndClaim("worker-1");
        jdbc.update("UPDATE jobs SET attempts = max_attempts WHERE id = ?", claimed);
        silentSince(claimed, Duration.ofMinutes(10));

        assertThat(queue.reclaimStale(Duration.ofMinutes(2))).isZero();

        Job abandoned = queue.find(claimed).orElseThrow();
        assertThat(abandoned.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(abandoned.getError()).containsEntry("code", "WORKER_LOST");
        assertThat(abandoned.getCompletedAt()).isNotNull();
    }

    @Test
    void theheartbeatOnlyTouchesTheWorkersOwnRows() {
        UUID mine = enqueueAndClaim("worker-1");
        UUID theirs = enqueueAndClaim("worker-2");

        assertThat(queue.touchHeartbeat("worker-1", List.of(mine, theirs))).isEqualTo(1);
        assertThat(queue.touchHeartbeat("worker-1", List.of())).isZero();
    }

    /**
     * Without this a rolling deploy leaves every in-flight job locked until
     * the collector notices — two minutes of dead time on every deploy.
     */
    @Test
    void shuttingDownHandsBackOnlyThisWorkersJobs() {
        UUID mine = enqueueAndClaim("worker-1");
        UUID theirs = enqueueAndClaim("worker-2");

        assertThat(queue.releaseLocks("worker-1")).isEqualTo(1);

        assertThat(queue.find(mine).orElseThrow().getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(queue.find(theirs).orElseThrow().getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    // ── Absolute rule 3: the user-facing half ────────────────────────────

    /**
     * The job id is the one identifier this system hands to a browser, and the
     * progress stream is addressed by it. A read that did not scope would be
     * an IDOR with a URL to type it into.
     */
    @Test
    void anotherUsersJobReadsAsAbsent() {
        Job mine = enqueue(JobType.GENERATION);
        UUID otherUserId = jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");

        assertThat(jobs.findById(UserContext.of(userId), mine.getId())).isPresent();
        assertThat(jobs.findById(UserContext.of(otherUserId), mine.getId())).isEmpty();
    }

    /**
     * An anonymous job has no owner at all, and asking a null owner whether it
     * equals anyone used to throw. "Not yours" is the honest answer.
     */
    @Test
    void anunownedJobReadsAsAbsentRatherThanThrowing() {
        Job anonymous = queue.enqueue(
                new Job(JobType.GENERATION, null, Map.of(), clock.instant()));

        assertThat(jobs.findById(UserContext.of(userId), anonymous.getId())).isEmpty();
    }

    /** Bolum 30.7: a double click produces one job, not two. */
    @Test
    void thesameIdempotencyKeyFindsTheJobItAlreadyMade() {
        Job first = new Job(JobType.GENERATION, userId, Map.of(), clock.instant());
        first.setIdempotencyKey("key-1");
        queue.enqueue(first);

        Optional<Job> found = jobs.findByIdempotencyKey(JobOwner.of(UserContext.of(userId)), "key-1");

        assertThat(found).map(Job::getId).contains(first.getId());
        assertThat(jobs.findByIdempotencyKey(JobOwner.of(UserContext.of(userId)), "key-2")).isEmpty();
        assertThat(jobs.findByIdempotencyKey(JobOwner.of(UserContext.of(userId)), null)).isEmpty();
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private Job enqueue(JobType type) {
        return queue.enqueue(new Job(type, userId,
                Map.of("jobDescription", "irrelevant"), clock.instant()));
    }

    private UUID enqueueAndClaim(String workerId) {
        enqueue(JobType.GENERATION);
        return queue.claim(workerId).orElseThrow();
    }

    private void silentSince(UUID jobId, Duration ago) {
        Instant last = clock.instant().minus(ago);
        jdbc.update("UPDATE jobs SET heartbeat_at = ? WHERE id = ?",
                java.sql.Timestamp.from(last), jobId);
    }
}
