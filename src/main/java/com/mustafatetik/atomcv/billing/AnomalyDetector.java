package com.mustafatetik.atomcv.billing;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Looking for the shapes that cost money (Bolum 44.3).
 *
 * <p>Two signals, and a third that is deliberately absent.
 *
 * <p><strong>The daily budget brake is not here, and that is on purpose.</strong>
 * Bolum 44.3 reads {@code counterRepo.totalCostToday()} and disables generation
 * above a threshold — the single most valuable thing in this section, because
 * it is what stops a runaway bill. It needs a cost per call, and nothing
 * records one yet: {@code ProviderChain} publishes an
 * {@code LlmInvocationEvent} that no listener persists, so
 * {@code llm_invocations} is empty and {@code usage_counters.cost_usd} is
 * always zero. A brake wired to that number would read zero forever and look
 * exactly like protection. It arrives with cost recording, and the flag it
 * would pull already exists and can be pulled by hand today
 * ({@link FeatureFlags#NEW_GENERATIONS}).
 *
 * <p>What the two signals here do is <em>report</em>. Bolum 44.3 also calls for
 * tightening a rate limit on the offender, and there is no rate limiter to
 * tighten; an operator reading the alert can pull the flag, which is the
 * blunter version of the same action.
 */
@Component
@ConditionalOnProperty(
        name = "atomcv.anomaly.enabled", havingValue = "true", matchIfMissing = true)
public class AnomalyDetector {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetector.class);

    /**
     * How far back a user's ordinary day is measured from.
     *
     * <p>Long enough that one busy Monday does not become the baseline, short
     * enough that a month-old habit does not excuse today.
     */
    private static final int BASELINE_DAYS = 7;

    private final JdbcTemplate jdbc;
    private final AnomalyProperties properties;
    private final MeterRegistry meters;
    private final Clock clock;

    AnomalyDetector(JdbcTemplate jdbc, AnomalyProperties properties, MeterRegistry meters,
            Clock clock) {

        this.jdbc = jdbc;
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
    }

    /** Bolum 44.3: every fifteen minutes. */
    @Scheduled(cron = "${atomcv.anomaly.cron:0 */15 * * * *}")
    public void detectAnomalies() {
        heavyUsers().forEach(this::report);
        int signups = signupsInLastHour();
        if (signups > properties.signupsPerHour()) {
            meters.counter("anomaly.signup_burst").increment();
            log.warn("Signup anomaly: {} in the last hour, threshold {}",
                    signups, properties.signupsPerHour());
        }
    }

    /**
     * Users generating far more today than they normally do.
     *
     * <p>A multiple of their own history rather than a fixed number, because a
     * fixed one is either useless for a heavy user or an alarm every day for a
     * light one. Users with no history are excluded by the join: a first day is
     * not an anomaly, and the daily quota is what bounds it.
     */
    List<HeavyUser> heavyUsers() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
        return jdbc.query("""
                SELECT today.subject_id, today.count, baseline.average
                FROM usage_counters today
                JOIN (
                    SELECT subject_id, avg(count) AS average
                    FROM usage_counters
                    WHERE metric = 'generation' AND subject_type = 'user'
                      AND period < ? AND period >= ?
                    GROUP BY subject_id
                ) baseline ON baseline.subject_id = today.subject_id
                WHERE today.metric = 'generation' AND today.subject_type = 'user'
                  AND today.period = ?
                  AND today.count > baseline.average * ?
                """,
                (row, index) -> new HeavyUser(
                        row.getString(1), row.getInt(2), row.getDouble(3)),
                today, today.minusDays(BASELINE_DAYS), today, properties.baselineFactor());
    }

    int signupsInLastHour() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE created_at >= ?", Integer.class,
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofHours(1))));
        return count == null ? 0 : count;
    }

    /**
     * The subject and the numbers, never anything they wrote (absolute rule 4).
     *
     * <p>A log line and a counter, not an action. There is no rate limiter to
     * tighten, and pulling the brake over one busy user would stop everybody —
     * that is a decision for whoever reads this.
     */
    private void report(HeavyUser user) {
        meters.counter("anomaly.heavy_user").increment();
        log.warn("Unusual usage: subject {} generated {} today against a baseline of {}",
                user.subjectId(), user.today(), String.format("%.1f", user.baseline()));
    }

    /** @param baseline the user's own average over the previous week */
    record HeavyUser(String subjectId, int today, double baseline) {
    }
}
