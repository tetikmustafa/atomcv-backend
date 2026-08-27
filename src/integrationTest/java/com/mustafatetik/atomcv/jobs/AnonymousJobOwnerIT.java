package com.mustafatetik.atomcv.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobOwner;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobRepository;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A job that belongs to somebody who has not signed up (Adim 3.6).
 *
 * <p>The queue has had a column for this since V1 and no way to use it: every
 * read was scoped to a user, so an anonymous caller could not poll the job
 * they had just started. What these cases hold is that they can — and that
 * they can reach exactly their own.
 */
class AnonymousJobOwnerIT extends AbstractIntegrationTest {

    @Autowired
    private JobQueue queue;

    @Autowired
    private JobRepository jobs;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Test
    void ananonymousCallerFindsTheJobTheyStarted() {
        JobOwner caller = anonymous();
        Job started = enqueueFor(caller, null);

        assertThat(jobs.findById(caller, started.getId())).map(Job::getId)
                .contains(started.getId());
        assertThat(jobs.findAll(caller)).extracting(Job::getId).contains(started.getId());
    }

    /**
     * And nobody else's. An anonymous owner is exactly as strong as the
     * cookie, which is what the anonymous profile is scoped by too — weaker
     * than an account, and deliberately so: what it protects is two hours of
     * work that the person chose not to sign up for.
     */
    @Test
    void anotherAnonymousCallerCannotReachIt() {
        Job started = enqueueFor(anonymous(), null);

        assertThat(jobs.findById(anonymous(), started.getId())).isEmpty();
    }

    @Test
    void anAccountCannotReachAnAnonymousJobEither() {
        Job started = enqueueFor(anonymous(), null);
        JobOwner account = JobOwner.of(UserContext.of(UUID.randomUUID()));

        assertThat(jobs.findById(account, started.getId())).isEmpty();
    }

    /**
     * <strong>V3, and the reason it had to exist.</strong> V1's unique index
     * was {@code (user_id, idempotency_key)} and an anonymous row has a null
     * owner — which Postgres counts as distinct from every other null, so the
     * double click Bolum 30.7 absorbs went through twice. The index now
     * coalesces the two owner columns, and the database refuses the second
     * row rather than the application hoping nobody clicks twice.
     */
    @Test
    void thesameKeyFromTheSameAnonymousCallerIsTheSameJob() {
        JobOwner caller = anonymous();
        Job first = enqueueFor(caller, "one-upload");

        assertThat(jobs.findByIdempotencyKey(caller, "one-upload")).map(Job::getId)
                .contains(first.getId());
        assertThatThrownBy(() -> enqueueFor(caller, "one-upload"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void thesameKeyFromTwoAnonymousCallersIsTwoJobs() {
        enqueueFor(anonymous(), "one-upload");

        JobOwner other = anonymous();
        Job theirs = enqueueFor(other, "one-upload");

        assertThat(jobs.findByIdempotencyKey(other, "one-upload")).map(Job::getId)
                .contains(theirs.getId());
    }

    // -- the owner itself --------------------------------------------------

    @Test
    void ajobBelongsToAnAccountOrToASessionAndNeverToBoth() {
        assertThatThrownBy(() -> new JobOwner(UUID.randomUUID(), "a-session"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobOwner(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobOwner(null, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The session id is the cookie; a value that printed itself would leak one. */
    @Test
    void ananonymousOwnerDoesNotPrintItsSession() {
        assertThat(anonymous().toString()).doesNotContain("session-");
    }

    // -- fixtures ----------------------------------------------------------

    private static JobOwner anonymous() {
        return JobOwner.anonymous(AnonymousSessionId.of("session-" + UUID.randomUUID()));
    }

    private Job enqueueFor(JobOwner owner, String idempotencyKey) {
        Job job = new Job(JobType.PROFILE_EXTRACT, owner.userId(), Map.of(), clock.instant());
        job.setAnonSessionId(owner.anonSessionId());
        job.setIdempotencyKey(idempotencyKey);
        Job saved = queue.enqueue(job);
        // Flushed, because the unique index is what this asserts about and a
        // pending insert has not met it yet.
        jdbc.queryForObject("SELECT count(*) FROM jobs", Integer.class);
        return saved;
    }
}
