package com.mustafatetik.atomcv.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * <p>The budget brake is the only signal that acts, and the tests for it are
 * about the two ways an emergency brake goes wrong: firing when it should not,
 * and lifting itself.
 */
class AnomalyDetectorIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Autowired
    private FeatureFlags flags;

    /**
     * Built rather than injected: the suite switches the detector off so its
     * cron cannot fire, and a disabled component has no bean. The same
     * arrangement {@code JobWorkerIT} uses, and for the same reason.
     */
    @org.springframework.beans.factory.annotation.Autowired
    private TightenedSubjects tightened;

    private AnomalyDetector detector;
    private AnomalyProperties properties;
    private LocalDate today;

    @BeforeEach
    void startFromQuietDays() {
        jdbc.update("DELETE FROM usage_counters");
        jdbc.update("DELETE FROM llm_invocations");
        jdbc.update("DELETE FROM feature_flags");
        properties = new AnomalyProperties(true, 5.0, 50, java.math.BigDecimal.TEN);
        detector = new AnomalyDetector(jdbc, properties, flags, tightened,
                new com.mustafatetik.atomcv.billing.QuotaProperties(0, 0, 0, 0, 0, null),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), clock);
        today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * <strong>The brake is global, and this class pulls it.</strong>
     *
     * <p>Bolum 44.3's brake is one row in {@code feature_flags} and an unset
     * flag reads as on, so leaving it pulled turns every generation in the
     * shared context into a 503 — for whichever class Gradle happens to run
     * next. It cost a run to find, and it read as sixteen unrelated failures
     * rather than as this: the cases here passed, and four other classes did
     * not.
     *
     * <p>Cleaning up in {@code @BeforeEach} was not enough for the same
     * reason. What matters is the state this class leaves behind, not the one
     * it starts from.
     */
    @AfterEach
    void releaseTheBrake() {
        jdbc.update("DELETE FROM feature_flags");
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

    // ── Bolum 44.3: the brake ────────────────────────────────────────────

    /** A quiet day changes nothing. The brake that fires on nothing is useless. */
    @Test
    void afullPassIsHarmlessOnAQuietSystem() {
        detector.detectAnomalies();

        assertThat(flags.isEnabled(FeatureFlags.NEW_GENERATIONS)).isTrue();
    }

    /** At the ceiling is not over it. */
    @Test
    void spendingUpToTheBudgetDoesNotPullTheBrake() {
        spent("9.999999");

        detector.detectAnomalies();

        assertThat(flags.isEnabled(FeatureFlags.NEW_GENERATIONS)).isTrue();
    }

    @Test
    void spendingOverTheBudgetPausesNewGenerations() {
        spent("10.000001");

        detector.detectAnomalies();

        assertThat(flags.isEnabled(FeatureFlags.NEW_GENERATIONS)).isFalse();
    }

    /**
     * Failures cost money too (Bolum 27.5): a provider that answers with a
     * schema error still bills for the tokens it produced. A total that
     * counted only successes would understate exactly the bad day that
     * matters.
     */
    @Test
    void failedCallsCountTowardsTheBill() {
        spent("6", "provider_error");
        spent("5", "success");

        detector.detectAnomalies();

        assertThat(flags.isEnabled(FeatureFlags.NEW_GENERATIONS)).isFalse();
    }

    /** Yesterday's bill is yesterday's. */
    @Test
    void ancientSpendingIsNotTodaysBill() {
        spentAt("50", clock.instant().minus(Duration.ofDays(2)));

        detector.detectAnomalies();

        assertThat(flags.isEnabled(FeatureFlags.NEW_GENERATIONS)).isTrue();
    }

    /**
     * The brake is one-way. Whether the cause was dealt with is not something
     * a scheduled job can know, and one that lifted itself would let the same
     * runaway repeat every night.
     */
    @Test
    void thebrakeIsNotReleasedWhenTheDayIsQuietAgain() {
        flags.disable(FeatureFlags.NEW_GENERATIONS);

        detector.detectAnomalies();

        assertThat(flags.isEnabled(FeatureFlags.NEW_GENERATIONS)).isFalse();
    }

    private void spent(String usd) {
        spent(usd, "success");
    }

    private void spent(String usd, String outcome) {
        spentAt(usd, clock.instant(), outcome);
    }

    private void spentAt(String usd, java.time.Instant at) {
        spentAt(usd, at, "success");
    }

    private void spentAt(String usd, java.time.Instant at, String outcome) {
        jdbc.update("""
                INSERT INTO llm_invocations (prompt_id, prompt_version, provider, model,
                                             cost_usd, outcome, created_at)
                VALUES ('job_analysis', 'v1', 'fake', 'fake-model', ?, ?, ?)
                """, new java.math.BigDecimal(usd), outcome, java.sql.Timestamp.from(at));
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
