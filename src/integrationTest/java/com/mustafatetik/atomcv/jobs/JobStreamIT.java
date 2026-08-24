package com.mustafatetik.atomcv.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobProgress;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.jobs.sse.SseRegistry;
import com.mustafatetik.atomcv.shared.security.LocalDevCurrentUser;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The progress stream (Bolum 30.6, EK D.6.4).
 *
 * <p>What is worth proving is not that events can be sent — it is the two
 * cases where a naive implementation goes quiet. A job that finished before
 * anyone subscribed must still say so, and a stream must end on a terminal
 * event rather than on a five-minute timeout. Both are the same failure from
 * the user's side: a page that spins over work that is already done.
 */
@AutoConfigureMockMvc
class JobStreamIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JobQueue queue;

    @Autowired
    private SseRegistry streams;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevCurrentUser localUser;

    @Autowired
    private Clock clock;

    @BeforeEach
    void startFromAnEmptyQueue() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM jobs");
    }

    /** A queued job says where it is at once, so the page never starts blank. */
    @Test
    void subscribingSendsTheCurrentStateImmediately() throws Exception {
        Job job = queued();
        job.setProgress(new JobProgress("B", "generation.phase.SCORING", 50));
        queue.save(job);

        String stream = streamOf(job);

        assertThat(stream).contains("event:phase")
                .contains("generation.phase.SCORING")
                .contains("\"pct\":50");
        assertThat(streams.watcherCount(job.getId())).isEqualTo(1);
    }

    /**
     * The worst failure in this subsystem, and the reason the snapshot on
     * connect exists: a job that finished between the 202 and the subscribe
     * would otherwise send nothing at all, forever.
     */
    @Test
    void ajobThatAlreadyFinishedStillSaysSo() throws Exception {
        Job job = queued();
        UUID generationId = UUID.randomUUID();
        job.succeed(Map.of("generationId", generationId.toString(), "pageCount", 1),
                clock.instant());
        queue.save(job);

        String stream = streamOf(job);

        assertThat(stream).contains("event:completed").contains(generationId.toString());
        // Nothing left to watch, so nothing is holding a connection open.
        assertThat(streams.watcherCount(job.getId())).isZero();
    }

    @Test
    void ajobThatAlreadyFailedCarriesItsCode() throws Exception {
        Job job = queued();
        job.fail(Map.of("code", "ALL_PROVIDERS_UNAVAILABLE"), clock.instant());
        queue.save(job);

        String stream = streamOf(job);

        assertThat(stream).contains("event:failed").contains("ALL_PROVIDERS_UNAVAILABLE");
    }

    /** Events arrive while the job runs, in the order they were published. */
    @Test
    void progressReachesAnOpenStream() throws Exception {
        Job job = queued();
        MvcResult result = subscribe(job);

        job.setProgress(new JobProgress("C", "generation.phase.RENDERING", 70));
        streams.progress(job);

        assertThat(result.getResponse().getContentAsString())
                .contains("generation.phase.RENDERING")
                .contains("\"pct\":70");
    }

    /**
     * A stream that ends on a terminal event is a spinner that stops. One that
     * ended on the five-minute timeout would look identical to a hung job.
     */
    @Test
    void theterminalEventClosesTheStream() throws Exception {
        Job job = queued();
        MvcResult result = subscribe(job);
        assertThat(streams.watcherCount(job.getId())).isEqualTo(1);

        job.succeed(Map.of("generationId", UUID.randomUUID().toString()), clock.instant());
        streams.terminal(job);

        assertThat(result.getResponse().getContentAsString()).contains("event:completed");
        assertThat(streams.watcherCount(job.getId())).isZero();
    }

    /** Every event carries an id, which orders one stream (EK D.6.4). */
    @Test
    void eventsAreNumbered() throws Exception {
        Job job = queued();
        MvcResult result = subscribe(job);

        job.setProgress(new JobProgress("C", "generation.phase.RENDERING", 70));
        streams.progress(job);

        assertThat(result.getResponse().getContentAsString()).contains("id:1").contains("id:2");
    }

    /**
     * Absolute rule 3. A stream carries the job's error, which names what a
     * profile is missing — this is not a less sensitive endpoint than the
     * status one.
     */
    @Test
    void anotherUsersStreamIsNotFound() throws Exception {
        UUID stranger = jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
        Job theirs = queue.enqueue(
                new Job(JobType.GENERATION, stranger, Map.of(), clock.instant()));

        mvc.perform(get("/api/v1/jobs/" + theirs.getId() + "/stream"))
                .andExpect(status().isNotFound());

        assertThat(streams.watcherCount(theirs.getId())).isZero();
    }

    /**
     * A watcher that goes away takes its bookkeeping with it, or an abandoned
     * job leaks an emitter and a counter for the life of the process.
     *
     * <p>Removal is called directly rather than by closing a connection:
     * MockMvc has no client to disconnect, and a test that pretended otherwise
     * would be asserting on its own stub. What the real callbacks do is hand
     * the same emitter to the same method.
     */
    @Test
    void awatcherThatGoesAwayIsForgotten() throws Exception {
        Job job = queued();
        var emitter = streams.subscribe(job);
        assertThat(streams.watcherCount(job.getId())).isEqualTo(1);

        streams.remove(job.getId(), emitter);

        assertThat(streams.watcherCount(job.getId())).isZero();
        // Removing something already gone is not an error: onCompletion and
        // onTimeout can both fire for one emitter.
        streams.remove(job.getId(), emitter);
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private Job queued() {
        return queue.enqueue(new Job(JobType.GENERATION, LocalDevCurrentUser.DEV_USER_ID,
                Map.of("jobDescription", "irrelevant"), clock.instant()));
    }

    private MvcResult subscribe(Job job) throws Exception {
        return mvc.perform(get("/api/v1/jobs/" + job.getId() + "/stream"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String streamOf(Job job) throws Exception {
        return subscribe(job).getResponse().getContentAsString();
    }
}
