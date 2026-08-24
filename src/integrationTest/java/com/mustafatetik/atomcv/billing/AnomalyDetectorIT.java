package com.mustafatetik.atomcv.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bolum 44.3's two signals, against real rows.
 *
 * <p>Called directly rather than left to the cron, which is off for the suite:
 * a detector firing on a timer would report on rows other tests are still
 * writing, and the failure would look like a flake.
 *
 * <p>The third signal — the daily budget brake — is not tested here because it
 * is not implemented, and it is not implemented because nothing records a cost
 * yet. That is written down in {@link AnomalyDetector}'s own javadoc rather
 * than left as an absence someone has to notice.
 */
class AnomalyDetectorIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    /**
     * Built rather than injected: the suite switches the detector off so its
     * cron cannot fire, and a disabled component has no bean. The same
     * arrangement {@code JobWorkerIT} uses, and for the same reason.
     */
    private AnomalyDetector detector;
    private AnomalyProperties properties;
    private LocalDate today;

    @BeforeEach
    void startFromQuietDays() {
        jdbc.update("DELETE FROM usage_counters");
        properties = new AnomalyProperties(true, 5.0, 50);
        detector = new AnomalyDetector(jdbc, properties,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), clock);
        today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * A multiple of the user's own history, not a fixed number: a fixed one is
     * useless for a heavy user and an alarm every day for a light one.
     */
    @Test
    void auserFarAboveTheirOwnBaselineIsReported() {
        String subject = UUID.randomUUID().toString();
        countOn(subject, today.minusDays(1), 2);
        countOn(subject, today.minusDays(2), 2);
        countOn(subject, today, (int) (2 * properties.baselineFactor()) + 1);

        assertThat(detector.heavyUsers())
                .singleElement()
                .satisfies(user -> {
                    assertThat(user.subjectId()).isEqualTo(subject);
                    assertThat(user.baseline()).isEqualTo(2.0);
                });
    }

    /** Busier than usual is not an anomaly; five times busier is. */
    @Test
    void auserMerelyHavingABusyDayIsNotReported() {
        String subject = UUID.randomUUID().toString();
        countOn(subject, today.minusDays(1), 4);
        countOn(subject, today, 8);

        assertThat(detector.heavyUsers()).isEmpty();
    }

    /**
     * A first day is not an anomaly. Without the join it would be — every new
     * user would divide by nothing — and the daily quota is what bounds them.
     */
    @Test
    void auserWithNoHistoryIsNotAnAnomaly() {
        countOn(UUID.randomUUID().toString(), today, 20);

        assertThat(detector.heavyUsers()).isEmpty();
    }

    /** The baseline is the previous week, not all of history. */
    @Test
    void adistantPastDoesNotCountAsBaseline() {
        String subject = UUID.randomUUID().toString();
        countOn(subject, today.minusDays(40), 1);
        countOn(subject, today, 20);

        assertThat(detector.heavyUsers()).isEmpty();
    }

    /**
     * Each user is measured against their own history, and this is the test
     * that says so.
     *
     * <p>Both users do the same amount today; only one of them is doing
     * something unusual. An earlier version gave the busy user no row for
     * today and passed with the join condition deliberately broken — it was
     * asserting that a user with no usage is not reported, which is a
     * different and much easier claim.
     */
    @Test
    void eachuserIsMeasuredAgainstTheirOwnHistory() {
        String quiet = UUID.randomUUID().toString();
        String busy = UUID.randomUUID().toString();
        countOn(quiet, today.minusDays(1), 1);
        countOn(busy, today.minusDays(1), 100);
        countOn(quiet, today, 10);
        countOn(busy, today, 10);

        assertThat(detector.heavyUsers())
                .extracting(AnomalyDetector.HeavyUser::subjectId)
                .containsExactly(quiet);
    }

    @Test
    void signupsAreCountedOverTheLastHourOnly() {
        int before = detector.signupsInLastHour();
        newUserCreated(clock.instant().minus(Duration.ofMinutes(10)));
        newUserCreated(clock.instant().minus(Duration.ofHours(3)));

        assertThat(detector.signupsInLastHour()).isEqualTo(before + 1);
    }

    /** The whole pass runs without touching anything it should not. */
    @Test
    void afullPassIsHarmlessOnAQuietSystem() {
        detector.detectAnomalies();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM feature_flags WHERE enabled = false", Integer.class))
                .as("reporting, not acting — Bolum 44.3 leaves the brake to an operator")
                .isZero();
    }

    private void countOn(String subject, LocalDate day, int count) {
        jdbc.update("""
                INSERT INTO usage_counters (subject_type, subject_id, metric, period, count)
                VALUES ('user', ?, 'generation', ?, ?)
                """, subject, day, count);
    }

    private void newUserCreated(java.time.Instant at) {
        jdbc.update("INSERT INTO users (email, created_at) VALUES (?, ?)",
                UUID.randomUUID() + "@example.com", java.sql.Timestamp.from(at));
    }
}
