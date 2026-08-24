package com.mustafatetik.atomcv.jobs.queue;

import com.mustafatetik.atomcv.shared.security.UserOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One unit of asynchronous work (Bolum 30).
 *
 * <p>The table is in {@code V1}; this is the mapping. There is no migration
 * and there must not be one (absolute rule 2).
 *
 * <p><strong>No {@code @Version}.</strong> The column does not exist, and it
 * should not: optimistic locking is not what keeps two workers off one job —
 * the {@code FOR UPDATE SKIP LOCKED} claim is, and it decides inside a single
 * statement rather than by letting both read and one lose. A version here
 * would be a second, weaker answer to a question already settled.
 *
 * <p>{@code userId} is nullable because Bolum 9's anonymous flow enqueues
 * without an account. Nothing produces one yet — the anonymous flow is
 * Stage 3 — but {@link #getOwnerId} can return null and everything that scopes
 * by owner has to survive that.
 */
@Entity
@Table(name = "jobs")
public class Job implements UserOwned {

    @Id
    private UUID id = UUID.randomUUID();

    @Convert(converter = JobType.JpaConverter.class)
    @Column(nullable = false, updatable = false)
    private JobType type;

    /** Null for an anonymous job (Stage 3). */
    private UUID userId;

    private String anonSessionId;

    /** Bolum 30.7: a double click produces one job, not two. */
    private String idempotencyKey;

    /** What the handler needs to do the work. Shape depends on the type. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> payload = Map.of();

    @Convert(converter = JobStatus.JpaConverter.class)
    @Column(nullable = false)
    private JobStatus status = JobStatus.QUEUED;

    /** Bolum 30.3, copied from the type at creation. Lower is taken first. */
    @Column(nullable = false)
    private short priority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JobProgress progress = JobProgress.NONE;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> result;

    /** The catalogue code and its params, never a stack trace or user text. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> error;

    @Column(nullable = false)
    private short attempts;

    @Column(nullable = false)
    private short maxAttempts = 3;

    /** The worker that holds the claim, for the zombie collector to reclaim. */
    private String lockedBy;

    private Instant lockedAt;

    private Instant heartbeatAt;

    /** Not before this instant. Bolum 30.5's backoff moves it forward. */
    @Column(nullable = false)
    private Instant runAfter = Instant.EPOCH;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.EPOCH;

    private Instant completedAt;

    protected Job() {
        // JPA
    }

    /**
     * @param userId null for an anonymous job
     * @param now    a parameter rather than a call to {@code now()}: a queue
     *               whose timestamps come from the clock cannot be tested for
     *               backoff or for staleness without sleeping
     */
    public Job(JobType type, UUID userId, Map<String, Object> payload, Instant now) {
        this.type = Objects.requireNonNull(type, "type");
        this.userId = userId;
        this.payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
        this.priority = type.priority();
        this.runAfter = Objects.requireNonNull(now, "now");
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public JobType getType() {
        return type;
    }

    /** Null for an anonymous job, which no scoped read may then return. */
    @Override
    public UUID getOwnerId() {
        return userId;
    }

    public String getAnonSessionId() {
        return anonSessionId;
    }

    public void setAnonSessionId(String anonSessionId) {
        this.anonSessionId = anonSessionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Map<String, Object> getPayload() {
        return payload == null ? Map.of() : Map.copyOf(payload);
    }

    public JobStatus getStatus() {
        return status;
    }

    public short getPriority() {
        return priority;
    }

    public JobProgress getProgress() {
        return progress == null ? JobProgress.NONE : progress;
    }

    public void setProgress(JobProgress progress) {
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    public Map<String, Object> getResult() {
        return result == null ? null : Map.copyOf(result);
    }

    public Map<String, Object> getError() {
        return error == null ? null : Map.copyOf(error);
    }

    public short getAttempts() {
        return attempts;
    }

    public short getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(short maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("A job is attempted at least once");
        }
        this.maxAttempts = maxAttempts;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public Instant getRunAfter() {
        return runAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    /**
     * Whether another attempt is left after the one just made.
     *
     * <p>Compared against {@code attempts}, which the claim has already
     * incremented — so a job with {@code maxAttempts = 3} that has just failed
     * its third attempt reports false.
     */
    public boolean hasAttemptsLeft() {
        return attempts < maxAttempts;
    }

    /** Done, with whatever the handler produced (Bolum 30.6's terminal event). */
    public void succeed(Map<String, Object> result, Instant now) {
        this.status = JobStatus.COMPLETED;
        this.result = result == null ? Map.of() : Map.copyOf(result);
        this.error = null;
        this.completedAt = now;
        releaseLock();
    }

    /**
     * Done, and it will not be tried again.
     *
     * @param error the catalogue code and its params — what the user is told,
     *              which is never a stack trace and never their own text
     *              (absolute rule 4)
     */
    public void fail(Map<String, Object> error, Instant now) {
        this.status = JobStatus.FAILED;
        this.error = error == null ? Map.of() : Map.copyOf(error);
        this.completedAt = now;
        releaseLock();
    }

    /**
     * Back to the queue, not before {@code runAfter} (Bolum 30.5).
     *
     * <p>The lock is released with it. A retried job that kept its
     * {@code locked_by} would be reclaimed by the zombie collector as well as
     * taken by the claim, and the same work would run twice.
     */
    public void retryAfter(Instant runAfter) {
        this.status = JobStatus.QUEUED;
        this.runAfter = Objects.requireNonNull(runAfter, "runAfter");
        releaseLock();
    }

    public void cancel(Instant now) {
        this.status = JobStatus.CANCELLED;
        this.completedAt = now;
        releaseLock();
    }

    private void releaseLock() {
        this.lockedBy = null;
        this.lockedAt = null;
        this.heartbeatAt = null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Job job && id.equals(job.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Identity and shape only. The payload is built from user content and is
     * treated as user content (absolute rule 4).
     */
    @Override
    public String toString() {
        return "Job[" + id + ", " + type + ", " + status + ", attempts=" + attempts + "]";
    }
}
