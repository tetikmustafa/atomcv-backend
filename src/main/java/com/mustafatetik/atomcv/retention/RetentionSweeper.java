package com.mustafatetik.atomcv.retention;

import java.sql.Timestamp;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Forgets the text this product was given, on a timer.
 *
 * <p>The Privacy Policy of Bolum 57 says how long content is kept. Until this
 * class there was no answer: {@code jobs.payload} carries a pasted posting or
 * an uploaded CV's extracted text, {@code generations.job_description} carries
 * the posting again, and nothing anywhere removed either. A retention promise
 * with no sweeper is a sentence, not a property of the system.
 *
 * <p><strong>Clearing, not deleting.</strong> The rows stay: a job's history
 * is what the queue's retries and the anomaly detector count, and a generation
 * is what a download re-renders from. What goes is the free text — everything
 * else about a generation is structure a person put there themselves.
 *
 * <p><strong>{@code jd_analysis} stays</strong>, and that is a decision rather
 * than an oversight. It is the shape of a posting, not its words — title,
 * seniority, the skills asked for — and it is what
 * {@code CoverLetterRegenerationService} reads to rewrite a letter. Clearing
 * it would break regeneration for every generation older than the window while
 * removing nothing a person wrote.
 *
 * <p>{@code JdbcTemplate} and no entity, as {@code AnomalyDetector} and
 * {@code UsageCounters} do: this is a set-based update over rows nobody owns,
 * and loading them one at a time to null a column would be slower and no safer.
 */
@Component
@ConditionalOnProperty(name = "atomcv.retention.enabled", matchIfMissing = true)
public class RetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(RetentionSweeper.class);

    /**
     * Terminal states only. A queued job's payload is what it is going to run
     * on, and a running job's is what it is running on now — an age test alone
     * would clear the input from under a worker that had been retrying for a
     * week.
     */
    private static final String CLEAR_PAYLOADS = """
            UPDATE jobs SET payload = '{}'::jsonb
            WHERE status IN ('completed', 'failed', 'cancelled')
              AND payload <> '{}'::jsonb
              AND coalesce(completed_at, created_at) < ?
            """;

    /**
     * {@code jd_hash} is left alone: it is a digest, it carries nothing back,
     * and it is how a cleared generation is still distinguishable from one run
     * in general CV mode — where the column was NULL from the start.
     */
    private static final String CLEAR_POSTINGS = """
            UPDATE generations SET job_description = NULL
            WHERE job_description IS NOT NULL
              AND created_at < ?
            """;

    /**
     * <strong>Deleting, not clearing, and it is the one place in this class that
     * does.</strong> Everything above keeps its row because somebody owns it. An
     * anonymous profile is owned by nobody — the session it belonged to is gone
     * — and Bolum 9 promises it is not kept. The row is the promise.
     *
     * <p>One statement for the whole tree: {@code sections}, {@code entries},
     * {@code atoms}, {@code atom_variants} and {@code tags} all reference
     * {@code profiles(id) ON DELETE CASCADE}, so the head going takes the CV
     * with it. Writing five deletes by hand would be a list to keep in step with
     * the schema, and the schema already says this.
     *
     * <p>{@code expires_at IS NOT NULL} is redundant against the comparison and
     * is there for the index: {@code idx_profiles_expires_at} is partial, and a
     * predicate that does not mention its condition does not use it.
     */
    private static final String DELETE_EXPIRED_PROFILES = """
            DELETE FROM profiles
            WHERE expires_at IS NOT NULL
              AND expires_at < ?
            """;

    private final JdbcTemplate jdbc;
    private final RetentionProperties properties;
    private final Clock clock;

    RetentionSweeper(JdbcTemplate jdbc, RetentionProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${atomcv.retention.cron:0 30 3 * * *}")
    public void sweep() {
        int payloads = clearPayloads();
        int postings = clearPostings();

        if (payloads > 0 || postings > 0) {
            // Counts, which is all there is to say. Absolute rule 4 covers the
            // rest, and a sweeper that logged what it was about to forget
            // would be the one place the text outlived its own retention.
            log.info("Retention sweep cleared {} job payloads and {} postings",
                    payloads, postings);
        }
    }

    /**
     * Its own schedule, minutes apart rather than once a night (Bolum 9).
     *
     * <p>The two sweeps answer different promises. The nightly one keeps a
     * retention window measured in days, where a few hours either way changes
     * nothing. This one keeps "we do not store it", and that sentence is only
     * true to the resolution of the pass that makes it true — a nightly sweep
     * would leave a two-hour session's CV in the database until the small hours.
     */
    @Scheduled(cron = "${atomcv.retention.anonymous-cron:0 */5 * * * *}")
    public void sweepAnonymousProfiles() {
        int profiles = deleteExpiredProfiles();
        if (profiles > 0) {
            // A count, and it is the whole of what there is to say: these rows
            // were somebody's CV (absolute rule 4).
            log.info("Deleted {} expired anonymous profiles", profiles);
        }
    }

    int deleteExpiredProfiles() {
        return jdbc.update(DELETE_EXPIRED_PROFILES, Timestamp.from(clock.instant()));
    }

    int clearPayloads() {
        return jdbc.update(CLEAR_PAYLOADS, cutoff(properties.jobPayload()));
    }

    int clearPostings() {
        return jdbc.update(CLEAR_POSTINGS, cutoff(properties.jobDescription()));
    }

    private Timestamp cutoff(java.time.Duration window) {
        return Timestamp.from(clock.instant().minus(window));
    }
}
