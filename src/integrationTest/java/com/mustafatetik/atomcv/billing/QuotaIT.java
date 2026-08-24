package com.mustafatetik.atomcv.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The daily allowance against a real Postgres (Bolum 44).
 *
 * <p><strong>Not {@code @Transactional}.</strong> The property worth proving
 * is what two concurrent requests do to one counter row, and wrapping them in
 * a third transaction would hide it — the same reason {@code JobQueueIT} is
 * written this way.
 */
class QuotaIT extends AbstractIntegrationTest {

    @Autowired
    private QuotaService quotas;

    @Autowired
    private UsageCounters counters;

    @Autowired
    private QuotaProperties limits;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private UserContext user;

    @BeforeEach
    void startFromAnUnusedDay() {
        jdbc.update("DELETE FROM usage_counters");
        user = UserContext.of(UUID.randomUUID());
    }

    @Test
    void thefirstUseCountsOneAndIsAllowed() {
        assertThat(quotas.consume(user, QuotaMetric.GENERATION).isErr()).isFalse();

        var usage = quotas.usage(user, QuotaMetric.GENERATION);
        assertThat(usage.used()).isEqualTo(1);
        assertThat(usage.limit()).isEqualTo(limits.generationsPerUser());
        assertThat(usage.remaining()).isEqualTo(limits.generationsPerUser() - 1);
    }

    @Test
    void theallowanceRunsOutAtTheLimitAndNotBefore() {
        for (int use = 1; use <= limits.generationsPerUser(); use++) {
            assertThat(quotas.consume(user, QuotaMetric.GENERATION).isErr())
                    .as("use %d of %d", use, limits.generationsPerUser()).isFalse();
        }

        assertThat(quotas.consume(user, QuotaMetric.GENERATION).isErr()).isTrue();
    }

    /**
     * The race this is written for. Two requests arriving together must get
     * different numbers — a read-then-write would let both see the same one,
     * and that is a race which never shows up in testing and shows up in the
     * bill.
     */
    @Test
    void concurrentUsesNeverShareANumber() throws Exception {
        int racers = 8;
        var barrier = new CyclicBarrier(racers);
        var pool = Executors.newFixedThreadPool(racers);
        try {
            List<Callable<Integer>> races = new ArrayList<>();
            for (int racer = 0; racer < racers; racer++) {
                races.add(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return counters.increment(QuotaSubject.of(user), QuotaMetric.GENERATION);
                });
            }

            List<Integer> counts = new ArrayList<>();
            for (Future<Integer> race : pool.invokeAll(races)) {
                counts.add(race.get());
            }

            assertThat(counts).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Bolum 44.1: one limit would let extraction eat the whole of it. */
    @Test
    void thetwoMetricsAreCountedApart() {
        quotas.consume(user, QuotaMetric.GENERATION);
        quotas.consume(user, QuotaMetric.GENERATION);

        assertThat(quotas.usage(user, QuotaMetric.GENERATION).used()).isEqualTo(2);
        assertThat(quotas.usage(user, QuotaMetric.PROFILE_EXTRACT).used()).isZero();
    }

    @Test
    void onepersonsUseIsNotAnothers() {
        var stranger = UserContext.of(UUID.randomUUID());
        quotas.consume(user, QuotaMetric.GENERATION);

        assertThat(quotas.usage(stranger, QuotaMetric.GENERATION).used()).isZero();
    }

    /** Bolum 44.2: a failure the user got no document out of is given back. */
    @Test
    void arefundGivesTheUnitBack() {
        quotas.consume(user, QuotaMetric.GENERATION);
        quotas.consume(user, QuotaMetric.GENERATION);

        quotas.refund(user, QuotaMetric.GENERATION);

        assertThat(quotas.usage(user, QuotaMetric.GENERATION).used()).isEqualTo(1);
    }

    /**
     * A refund for something never counted would push the row negative and
     * hand out free allowance — a job failing after a reclaim can refund more
     * than once.
     */
    @Test
    void refundsNeverGoBelowZero() {
        quotas.refund(user, QuotaMetric.GENERATION);
        quotas.consume(user, QuotaMetric.GENERATION);
        quotas.refund(user, QuotaMetric.GENERATION);
        quotas.refund(user, QuotaMetric.GENERATION);

        assertThat(quotas.usage(user, QuotaMetric.GENERATION).used()).isZero();
    }

    /**
     * F-007, and the assertion is pinned to a fixed instant on purpose.
     *
     * <p>Comparing {@code today()} against the wall clock is a test that tells
     * the truth for twenty-one hours a day: this machine runs at UTC+3, so a
     * counter following the server's zone agrees with UTC until 21:00 local and
     * disagrees after. Measured — with the reading swapped to
     * {@code Europe/Istanbul}, the wall-clock version passed and this one
     * fails.
     *
     * <p>22:30 UTC is 01:30 the next day in Istanbul, so the two readings name
     * different days and only one of them matches the row.
     */
    @Test
    void thedayIsTheUtcDayEvenLateInTheEvening() {
        var lateAtNight = new UsageCounters(jdbc,
                Clock.fixed(java.time.Instant.parse("2026-08-24T22:30:00Z"), ZoneOffset.UTC));

        assertThat(lateAtNight.today()).isEqualTo(LocalDate.of(2026, 8, 24));
    }

    /** And what is written is what {@code today()} says. */
    @Test
    void thestoredPeriodIsThatSameDay() {
        quotas.consume(user, QuotaMetric.GENERATION);

        LocalDate stored = jdbc.queryForObject(
                "SELECT period FROM usage_counters LIMIT 1", LocalDate.class);
        assertThat(stored).isEqualTo(counters.today());
        assertThat(stored).isEqualTo(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    /** The counter resets at UTC midnight, and resetsAt says exactly when. */
    @Test
    void theresetIsAnAbsoluteInstantAtUtcMidnight() {
        var usage = quotas.usage(user, QuotaMetric.GENERATION);

        assertThat(usage.resetsAt())
                .isEqualTo(counters.today().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        assertThat(usage.resetsAt()).isAfter(clock.instant());
    }
}
