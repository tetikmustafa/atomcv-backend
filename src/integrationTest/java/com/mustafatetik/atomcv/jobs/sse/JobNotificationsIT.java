package com.mustafatetik.atomcv.jobs.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobProgress;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The way out of an in-process registry, against a real Postgres.
 *
 * <p>The in-process registry has an expiry date — two instances, and a watcher
 * connected to A hears nothing about a job running on B — and names {@code
 * LISTEN/NOTIFY} as the way out. The readiness table already claims it.
 *
 * <p><strong>The second instance is the {@code pg_notify} call itself.</strong>
 * Standing up a second application context to send it would be testing
 * Spring's ability to start twice; what has to be true is narrower and is
 * exactly what crosses the wire — an announcement this process did not make
 * reaches the watchers this process is holding, and one it did make does not
 * reach them twice.
 *
 * <p>Waiting rather than asserting immediately, because a listener is a thread:
 * these are the only assertions in the suite that are about something arriving.
 */
class JobNotificationsIT extends AbstractIntegrationTest {

    /** Generous: the listener's own poll is a second, and CI is slower. */
    private static final long PATIENCE_MS = 15_000;

    /** Long enough that several polls have certainly run. */
    private static final long LONG_ENOUGH_TO_BE_SURE_MS = 3_500;

    @Autowired
    private SseRegistry registry;

    @Autowired
    private JobNotifications notifications;

    @Autowired
    private JobQueue queue;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * What the other instance does: write the row, then announce it. The
     * announcement carries an identifier, so this side reads the row — which
     * is the same row a reconnecting client would be caught up from.
     */
    @Test
    void anannouncementFromAnotherInstanceReachesThisOnesWatchers() {
        Job job = watched();
        long onSubscribe = registry.eventsSent(job.getId());

        job.setProgress(new JobProgress("D", "generation.phase.REWRITING", 60, null));
        queue.save(job);
        announceAs("another-instance", job.getId());

        waitFor("the phase to reach this instance's watcher",
                () -> registry.eventsSent(job.getId()) > onSubscribe);
    }

    /** A terminal announcement closes the stream, the same as a local one. */
    @Test
    void aterminalAnnouncementFromAnotherInstanceClosesTheStream() {
        Job job = watched();
        assertThat(registry.watcherCount(job.getId())).isEqualTo(1);

        job.succeed(Map.of("generationId", UUID.randomUUID().toString()), Instant.now());
        queue.save(job);
        announceAs("another-instance", job.getId());

        waitFor("the stream to close", () -> registry.watcherCount(job.getId()) == 0);
    }

    /**
     * <strong>An instance does not listen to itself.</strong> The notification
     * goes out on a pooled connection and comes back on the listening one, so
     * without the sender's id every local watcher would be sent everything
     * twice — and a progress bar that walks each step twice is worse than one
     * that does not move at all.
     */
    @Test
    void anannouncementThisInstanceMadeIsNotDeliveredBackToIt() {
        Job job = watched();
        long onSubscribe = registry.eventsSent(job.getId());

        job.setProgress(new JobProgress("B", "generation.phase.SCORING", 50, null));
        queue.save(job);
        announceAs(notifications.instanceId(), job.getId());

        stayTrue("nothing is delivered back to the sender",
                () -> registry.eventsSent(job.getId()) == onSubscribe);
    }

    /**
     * An announcement about a job nobody here is watching is not a query. With
     * more than one instance that is most announcements, and a listener that
     * loaded every row would turn one instance's work into everybody's.
     */
    @Test
    void anannouncementAboutAnunwatchedJobIsIgnored() {
        Job job = aqueuedJob();

        announceAs("another-instance", job.getId());

        stayTrue("no watcher and no counter appear",
                () -> registry.watcherCount(job.getId()) == 0
                        && registry.eventsSent(job.getId()) == 0);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /** A queued job with one stream open on it. */
    private Job watched() {
        Job job = aqueuedJob();
        registry.subscribe(job);
        return job;
    }

    /** The dev user, because `jobs.user_id` is a foreign key to a real row. */
    private Job aqueuedJob() {
        return queue.enqueue(new Job(JobType.GENERATION, LocalDevUser.DEV_USER_ID,
                Map.of("test", true), Instant.now()));
    }

    private void announceAs(String instance, UUID jobId) {
        jdbc.queryForObject("SELECT pg_notify(?, ?)", String.class, JobNotifications.CHANNEL,
                "{\"job\":\"" + jobId + "\",\"from\":\"" + instance + "\"}");
    }

    private static void waitFor(String what, BooleanSupplier until) {
        long deadline = System.currentTimeMillis() + PATIENCE_MS;
        while (System.currentTimeMillis() < deadline) {
            if (until.getAsBoolean()) {
                return;
            }
            pause(50);
        }
        throw new AssertionError("Waited " + PATIENCE_MS + "ms for " + what
                + " and it never happened. Either the listener is not running or the "
                + "announcement was not delivered.");
    }

    private static void stayTrue(String what, BooleanSupplier holds) {
        long deadline = System.currentTimeMillis() + LONG_ENOUGH_TO_BE_SURE_MS;
        while (System.currentTimeMillis() < deadline) {
            assertThat(holds.getAsBoolean()).as(what).isTrue();
            pause(50);
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting", interrupted);
        }
    }
}
