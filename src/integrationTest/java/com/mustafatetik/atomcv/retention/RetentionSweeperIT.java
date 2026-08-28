package com.mustafatetik.atomcv.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bolum 57's retention window, exercised against a real schema.
 *
 * <p>The sweeper is off for the rest of the suite — a scheduled clear over
 * shared tables would empty rows other tests assert on. So this class turns it
 * back on in a context of its own, which is also the only thing that proves
 * Spring can build the bean at all: CLAUDE.md's rule that a component the whole
 * suite switches off has unverified wiring was written after a worker that no
 * test had ever asked Spring to construct failed on {@code make dev}.
 *
 * <p>The cron is moved to a date that will not arrive during a test run. The
 * bean is built and called by hand; a scheduler firing halfway through an
 * assertion would be a flake nobody could reproduce.
 */
@SpringBootTest(properties = {
        // Inherited by hand, and it has to be: @SpringBootTest on a subclass
        // replaces the parent's attributes rather than adding to them, so
        // declaring `properties` here silently dropped all three of
        // AbstractIntegrationTest's switches -- including the job worker,
        // whose scheduler then claimed rows JobQueueIT was asserting on. It
        // read as an unrelated failure in another class.
        "atomcv.jobs.worker.enabled=false",
        "atomcv.anomaly.enabled=false",
        "atomcv.retention.enabled=true",
        "atomcv.retention.cron=0 0 3 1 1 *"})
class RetentionSweeperIT extends AbstractIntegrationTest {

    @Autowired
    private RetentionSweeper sweeper;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID profileId;

    /**
     * The dev user's profile, not a new one: {@code profiles.user_id} is
     * unique and {@code DevSeeder} has already made it. Nothing is deleted
     * here either — the suite shares one database, and clearing this user's
     * generations would take rows another class is asserting on.
     */
    @BeforeEach
    void profile() {
        profileId = jdbc.queryForObject("SELECT id FROM profiles WHERE user_id = ?",
                UUID.class, LocalDevUser.DEV_USER_ID);
    }

    @Test
    void aFinishedJobLosesTheInputItRanOn() {
        UUID old = job("completed", "60 days");
        UUID recent = job("completed", "1 day");

        assertThat(sweeper.clearPayloads()).isGreaterThanOrEqualTo(1);

        assertThat(payloadOf(old)).isEqualTo("{}");
        assertThat(payloadOf(recent)).contains("a posting");
    }

    /**
     * The age test alone would clear the input from under a worker that had
     * been retrying the same job for a week.
     */
    @Test
    void aJobStillWaitingKeepsItsInputHoweverOldItIs() {
        UUID queued = job("queued", "60 days");
        UUID running = job("running", "60 days");

        sweeper.clearPayloads();

        assertThat(payloadOf(queued)).contains("a posting");
        assertThat(payloadOf(running)).contains("a posting");
    }

    /** Nothing left to clear is not a row to rewrite every night. */
    @Test
    void aSecondSweepFindsNothingToDo() {
        job("completed", "60 days");

        assertThat(sweeper.clearPayloads()).isGreaterThanOrEqualTo(1);
        assertThat(sweeper.clearPayloads()).isZero();
    }

    @Test
    void aGenerationLosesThePostingItWasWrittenAgainst() {
        UUID old = generation("60 days", "a posting");
        UUID recent = generation("1 day", "a posting");

        assertThat(sweeper.clearPostings()).isGreaterThanOrEqualTo(1);

        assertThat(postingOf(old)).isNull();
        assertThat(postingOf(recent)).isEqualTo("a posting");
    }

    /**
     * The digest stays, and it is what still separates a cleared generation
     * from one run in general CV mode — where the posting was NULL from the
     * start and always will be. Without it, forgetting the text would also
     * forget that there had been one.
     */
    @Test
    void theDigestSurvivesSoAClearedGenerationIsStillNotAGeneralOne() {
        UUID cleared = generation("60 days", "a posting");
        UUID general = generation("60 days", null);

        sweeper.clearPostings();

        assertThat(digestOf(cleared)).isNotNull();
        assertThat(digestOf(general)).isNull();
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private UUID job(String status, String age) {
        return jdbc.queryForObject("""
                INSERT INTO jobs (type, user_id, status, payload, created_at, completed_at)
                VALUES ('generation', ?, ?, '{"jobDescription":"a posting"}'::jsonb,
                        now() - CAST(? AS interval), now() - CAST(? AS interval))
                RETURNING id
                """, UUID.class, LocalDevUser.DEV_USER_ID, status, age, age);
    }

    private UUID generation(String age, String posting) {
        return jdbc.queryForObject("""
                INSERT INTO generations
                    (user_id, profile_id, job_description, jd_hash,
                     options, selection_state, engine_version, status, created_at)
                VALUES (?, ?, ?, ?, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, 'completed',
                        now() - CAST(? AS interval))
                RETURNING id
                """, UUID.class, LocalDevUser.DEV_USER_ID, profileId, posting,
                posting == null ? null : "a-digest", age);
    }

    private String payloadOf(UUID job) {
        return jdbc.queryForObject("SELECT payload::text FROM jobs WHERE id = ?",
                String.class, job);
    }

    private String postingOf(UUID generation) {
        return jdbc.queryForObject("SELECT job_description FROM generations WHERE id = ?",
                String.class, generation);
    }

    private String digestOf(UUID generation) {
        return jdbc.queryForObject("SELECT jd_hash FROM generations WHERE id = ?",
                String.class, generation);
    }
}
