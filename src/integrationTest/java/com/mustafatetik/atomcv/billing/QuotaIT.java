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
    private FeatureFlags flags;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private UserContext user;

    @BeforeEach
    void startFromAnUnusedDay() {
        jdbc.update("DELETE FROM usage_counters");
        jdbc.update("DELETE FROM feature_flags");
        user = UserContext.of(UUID.randomUUID());
    }

    @Test
    void thefirstUseCountsOneAndIsAllowed() {
        assertThat(quotas.consume(QuotaSubject.of(user), QuotaMetric.GENERATION).isErr()).isFalse();

        var usage = quotas.usage(QuotaSubject.of(user), QuotaMetric.GENERATION);
        assertThat(usage.used()).isEqualTo(1);
        assertThat(usage.limit()).isEqualTo(limits.generationsPerUser());
        assertThat(usage.remaining()).isEqualTo(limits.generationsPerUser() - 1);
    }

    @Test
    void theallowanceRunsOutAtTheLimitAndNotBefore() {
        for (int use = 1; use <= limits.generationsPerUser(); use++) {
            assertThat(quotas.consume(QuotaSubject.of(user), QuotaMetric.GENERATION).isErr())
                    .as("use %d of %d", use, limits.generationsPerUser()).isFalse();
        }

        assertThat(quotas.consume(QuotaSubject.of(user), QuotaMetric.GENERATION).isErr()).isTrue();
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

    /**
     * F-012: what the screen reads once the allowance is gone.
     *
     * <p>A refused request keeps its unit — that is what stops a user who is
     * already over from hammering the endpoint with the number never moving —
     * so the row itself runs past the limit. The wire says both things: what
     * was spent, and how many times they asked.
     */
    @Test
    void refusedRequestsShowUpAsAttemptsAndNotAsConsumption() {
        int limit = limits.profileExtractsPerUser();
        for (int i = 0; i < limit + 2; i++) {
            quotas.consume(QuotaSubject.of(user), QuotaMetric.PROFILE_EXTRACT);
        }

        var usage = quotas.usage(QuotaSubject.of(user), QuotaMetric.PROFILE_EXTRACT);
        assertThat(counters.used(QuotaSubject.of(user), QuotaMetric.PROFILE_EXTRACT))
                .as("the row keeps counting; that is the abuse brake")
                .isEqualTo(limit + 2);
        assertThat(usage.used()).isEqualTo(limit);
        assertThat(usage.attempted()).isEqualTo(limit + 2);
        assertThat(usage.remaining()).isZero();
    }

    /** Bolum 44.1: one limit would let extraction eat the whole of it. */
    @Test
    void thetwoMetricsAreCountedApart() {
        quotas.consume(QuotaSubject.of(user), QuotaMetric.GENERATION);
        quotas.consume(QuotaSubject.of(user), QuotaMetric.GENERATION);

        assertThat(quotas.usage(QuotaSubject.of(user), QuotaMetric.GENERATION).used()).isEqualTo(2);
        assertThat(quotas.usage(QuotaSubject.of(user), QuotaMetric.PROFILE_EXTRACT).used()).isZero();
    }

    @Test
    void onepersonsUseIsNotAnothers() {
        var stranger = UserContext.of(UUID.randomUUID());
        quotas.consume(QuotaSubject.of(user), QuotaMetric.GENERATION);

        assertThat(quotas.usage(QuotaSubject.of(stranger), QuotaMetric.GENERATION).used()).isZero();
    }

    /**
     * Bolum 44.1's anonymous pair, and they are lower on purpose: an address
     * is a weaker claim about who is asking than an account, and it is what
     * somebody spending our money rotates.
     */
    @Test
    void anaddressHasItsOwnSmallerAllowance() {
        var caller = QuotaSubject.ofAddress("203.0.113.7");

        assertThat(quotas.usage(caller, QuotaMetric.GENERATION).limit()).isEqualTo(5);
        assertThat(quotas.usage(caller, QuotaMetric.PROFILE_EXTRACT).limit()).isEqualTo(3);
        assertThat(quotas.usage(QuotaSubject.of(user), QuotaMetric.GENERATION).limit())
                .isEqualTo(20);
    }

    @Test
    void anaddressRunsOutAtItsOwnCeiling() {
        var caller = QuotaSubject.ofAddress("203.0.113.8");

        for (int i = 0; i < 3; i++) {
            assertThat(quotas.consume(caller, QuotaMetric.PROFILE_EXTRACT).isErr()).isFalse();
        }

        assertThat(quotas.consume(caller, QuotaMetric.PROFILE_EXTRACT).isErr()).isTrue();
    }

    /** One office router is not one person, but two addresses are two subjects. */
    @Test
    void twoAddressesAreTwoAllowances() {
        quotas.consume(QuotaSubject.ofAddress("203.0.113.9"), QuotaMetric.GENERATION);

        assertThat(quotas.usage(QuotaSubject.ofAddress("203.0.113.10"), QuotaMetric.GENERATION)
                .used()).isZero();
    }

    /** An address and an account are different subjects even if one becomes the other. */
    @Test
    void anaddressAndAnAccountDoNotShareACounter() {
        quotas.consume(QuotaSubject.ofAddress("203.0.113.11"), QuotaMetric.GENERATION);

        assertThat(quotas.usage(QuotaSubject.of(user), QuotaMetric.GENERATION).used()).isZero();
    }

    /** Bolum 44.2: a failure the user got no document out of is given back. */
    @Test
    void arefundGivesTheUnitBack() {
        quotas.consume(QuotaSubject.of(user), QuotaMetric.GENERATION);
        quotas.consume(QuotaSubject.of(user), QuotaMetric.GENERATION);

        quotas.refund(QuotaSubject.of(user), QuotaMetric.GENERATION);

        assertThat(quotas.usage(QuotaSubject.of(user), QuotaMetric.GENERATION).used()).isEqualTo(1);
    }

    /**
     * A refund for something never counted would push the row negative and
     * hand out free allowance — a job failing after a reclaim can refund more
     * than once.
     */
    @Test
    void refundsNeverGoBelowZero() {
        quotas.refund(QuotaSubject.of(user), QuotaMetric.GENERATION);
        quotas.consume(QuotaSubject.of(user), QuotaMetric.GENERATION);
        quotas.refund(QuotaSubject.of(user), QuotaMetric.GENERATION);
        quotas.refund(QuotaSubject.of(user), QuotaMetric.GENERATION);

        assertThat(quotas.usage(QuotaSubject.of(user), QuotaMetric.GENERATION).used()).isZero();
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
        quotas.consume(QuotaSubject.of(user), QuotaMetric.GENERATION);

        LocalDate stored = jdbc.queryForObject(
                "SELECT period FROM usage_counters LIMIT 1", LocalDate.class);
        assertThat(stored).isEqualTo(counters.today());
        assertThat(stored).isEqualTo(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    // ── Bolum 44.3: the brake ────────────────────────────────────────────

    /**
     * An empty table serves. A deployment that has never touched a flag must
     * not behave as if everything were switched off.
     */
    @Test
    void anunsetFlagIsOn() {
        jdbc.update("DELETE FROM feature_flags");

        assertThat(flags.isEnabled(FeatureFlags.NEW_GENERATIONS)).isTrue();
        assertThat(flags.isEnabled("something.nobody.has.named")).isTrue();
    }

    @Test
    void thebrakeGoesOnAndComesOffAgain() {
        flags.disable(FeatureFlags.NEW_GENERATIONS);
        assertThat(flags.isEnabled(FeatureFlags.NEW_GENERATIONS)).isFalse();

        flags.enable(FeatureFlags.NEW_GENERATIONS);
        assertThat(flags.isEnabled(FeatureFlags.NEW_GENERATIONS)).isTrue();
    }

    /**
     * Read from the database every time, never cached. The flag exists to be
     * flipped in the middle of an incident, and a cache is a delay between the
     * decision and the effect.
     */
    @Test
    void aflagFlippedOutsideTheProcessIsSeenAtOnce() {
        assertThat(flags.isEnabled(FeatureFlags.NEW_GENERATIONS)).isTrue();

        jdbc.update("INSERT INTO feature_flags (key, enabled) VALUES (?, false)"
                + " ON CONFLICT (key) DO UPDATE SET enabled = false",
                FeatureFlags.NEW_GENERATIONS);

        assertThat(flags.isEnabled(FeatureFlags.NEW_GENERATIONS)).isFalse();
    }

    /** The counter resets at UTC midnight, and resetsAt says exactly when. */
    @Test
    void theresetIsAnAbsoluteInstantAtUtcMidnight() {
        var usage = quotas.usage(QuotaSubject.of(user), QuotaMetric.GENERATION);

        assertThat(usage.resetsAt())
                .isEqualTo(counters.today().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        assertThat(usage.resetsAt()).isAfter(clock.instant());
    }
}
