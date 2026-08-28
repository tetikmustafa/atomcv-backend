package com.mustafatetik.atomcv.billing;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code usage_counters}, read and written the only way it can be safely
 * (Bolum 44).
 *
 * <p><strong>The increment is one statement.</strong> {@code INSERT … ON
 * CONFLICT DO UPDATE … RETURNING count} decides inside the database, so two
 * requests arriving together get 1 and 2 rather than both reading 0 and both
 * writing 1. A read-then-write here is not a race that shows up in testing —
 * it is a race that shows up as a bill.
 *
 * <p>SQL rather than JPA for the same reason the queue's claim is: there is no
 * JPQL for {@code ON CONFLICT}, and an entity would invite exactly the
 * read-modify-write this exists to avoid.
 *
 * <p><strong>The day is UTC and that is a decision (F-007).</strong>
 * {@code period} is a timezone-less {@code DATE}, and UTC is the only reading
 * that keeps one row meaning one day whatever the server is set to. Written as
 * {@code ZoneOffset.UTC} explicitly rather than through the clock's own zone:
 * this machine runs at UTC+3, and a counter that followed it would roll over
 * at a different instant here than on the runner — tests would start passing
 * only before 21:00.
 */
@Repository
public class UsageCounters {

    private static final String INCREMENT = """
            INSERT INTO usage_counters (subject_type, subject_id, metric, period, count, cost_usd)
            VALUES (?, ?, ?, ?, 1, 0)
            ON CONFLICT (subject_type, subject_id, metric, period)
            DO UPDATE SET count = usage_counters.count + 1
            RETURNING count
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    UsageCounters(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The UTC day a counter belongs to right now (F-007). */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * Counts one use and says what the total became.
     *
     * @return the count <em>after</em> this use, so the caller compares it
     *         against the limit without a second read — and without the window
     *         between them that a second read would open
     */
    @Transactional
    public int increment(QuotaSubject subject, QuotaMetric metric) {
        Integer count = jdbc.queryForObject(INCREMENT, Integer.class,
                subject.type().wireValue(), subject.id(), metric.wireValue(), today());
        return count == null ? 0 : count;
    }

    /**
     * Gives one back (Bolum 44.2).
     *
     * <p>Floored at zero. A refund for a use that was never counted — a job
     * failing twice, a retry that refunded on each attempt — would otherwise
     * push the row negative and hand out free quota that nobody asked for.
     */
    @Transactional
    public void refund(QuotaSubject subject, QuotaMetric metric) {
        jdbc.update("""
                UPDATE usage_counters SET count = greatest(count - 1, 0)
                WHERE subject_type = ? AND subject_id = ? AND metric = ? AND period = ?
                """, subject.type().wireValue(), subject.id(), metric.wireValue(), today());
    }

    /**
     * Everything ever counted against this subject, in every period.
     *
     * <p><strong>Düzeltme — Bolum 57.4 says the cascade handles this, and it
     * does not.</strong> The table is keyed by {@code (subject_type,
     * subject_id)} rather than by a foreign key, because a subject may be an
     * address or an anonymous session as easily as an account. No
     * {@code ON DELETE} reaches it, so a deleted account would leave its
     * counters behind: an identifier with a number beside it, surviving the
     * erasure that was meant to remove it.
     *
     * @return how many rows went, for the deletion record
     */
    @Transactional
    public int forget(QuotaSubject subject) {
        return jdbc.update(
                "DELETE FROM usage_counters WHERE subject_type = ? AND subject_id = ?",
                subject.type().wireValue(), subject.id());
    }

    /**
     * What has been used today.
     *
     * <p>{@code query} rather than {@code queryForObject}: no row is the
     * ordinary case — most users have not generated anything yet today — and
     * {@code queryForObject} answers that with an exception.
     */
    public int used(QuotaSubject subject, QuotaMetric metric) {
        return jdbc.query("""
                SELECT count FROM usage_counters
                WHERE subject_type = ? AND subject_id = ? AND metric = ? AND period = ?
                """, (row, index) -> row.getInt(1),
                subject.type().wireValue(), subject.id(), metric.wireValue(), today())
                .stream().findFirst().orElse(0);
    }
}
