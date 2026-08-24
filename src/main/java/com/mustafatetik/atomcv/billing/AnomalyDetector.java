package com.mustafatetik.atomcv.billing;

import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
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
 * <p>Three signals now. <strong>The budget brake is the only one that acts</strong>
 * — it disables {@link FeatureFlags#NEW_GENERATIONS} — and that asymmetry is
 * deliberate: a day's bill above the ceiling is about the deployment and
 * stopping everybody is the correct response, while one busy user is about one
 * person and stopping everybody is not. Bolum 44.3 asks for tightening a rate
 * limit on that user instead; there is no rate limiter yet, so those two
 * signals report and an operator decides.
 *
 * <p>The brake is one-way. Nothing here turns generation back on, because
 * nothing here knows whether the cause was dealt with — the budget resets at
 * midnight and the flag does not, on purpose.
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
    private final FeatureFlags flags;
    private final MeterRegistry meters;
    private final Clock clock;

    AnomalyDetector(JdbcTemplate jdbc, AnomalyProperties properties, FeatureFlags flags,
            MeterRegistry meters, Clock clock) {

        this.flags = flags;
        this.jdbc = jdbc;
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
    }

    /** Bolum 44.3: every fifteen minutes. */
    @Scheduled(cron = "${atomcv.anomaly.cron:0 */15 * * * *}")
    public void detectAnomalies() {
        BigDecimal spentToday = costToday();
        if (spentToday.compareTo(properties.dailyBudgetUsd()) > 0) {
            brake(spentToday);
        }

        heavyUsers().forEach(this::report);
        int signups = signupsInLastHour();
        if (signups > properties.signupsPerHour()) {
            meters.counter("anomaly.signup_burst").increment();
            log.warn("Signup anomaly: {} in the last hour, threshold {}",
                    signups, properties.signupsPerHour());
        }
    }

    /**
     * What today's calls have cost, failures included (Bolum 27.5): a provider
     * that answers with a schema error still bills for the tokens.
     */
    BigDecimal costToday() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
        BigDecimal total = jdbc.queryForObject(
                "SELECT coalesce(sum(cost_usd), 0) FROM llm_invocations WHERE created_at >= ?",
                BigDecimal.class, java.sql.Timestamp.from(
                        today.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)));
        return total == null ? BigDecimal.ZERO : total;
    }

    /**
     * Bolum 44.3's emergency brake.
     *
     * <p>Pulled here and never released here. Whether the cause was dealt with
     * is not something a scheduled job can know, and a brake that lifted
     * itself at midnight would let the same runaway repeat every night.
     */
    private void brake(BigDecimal spentToday) {
        if (!flags.isEnabled(FeatureFlags.NEW_GENERATIONS)) {
            return;
        }
        meters.counter("anomaly.budget_exceeded").increment();
        log.error("Daily budget exceeded: ${} against ${}; pausing new generations",
                spentToday, properties.dailyBudgetUsd());
        flags.disable(FeatureFlags.NEW_GENERATIONS);
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
