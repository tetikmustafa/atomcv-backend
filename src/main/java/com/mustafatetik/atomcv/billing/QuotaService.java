package com.mustafatetik.atomcv.billing;

import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The daily allowance, taken and given back (Bolum 44.1, 44.2).
 *
 * <p><strong>Counted when the work is queued, not when it succeeds.</strong>
 * Bolum 44.2 says so and the alternative is the hole: a user whose generations
 * all fail would pay nothing and could queue forever, and every one of those
 * costs an LLM call before it fails.
 *
 * <p>The count and the check are one statement. Reading the counter and then
 * writing it would let two requests arriving together both see nineteen and
 * both proceed — a race that never appears in testing and appears in the bill.
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final UsageCounters counters;
    private final QuotaProperties limits;
    private final Clock clock;

    QuotaService(UsageCounters counters, QuotaProperties limits, Clock clock) {
        this.counters = counters;
        this.limits = limits;
        this.clock = clock;
    }

    /**
     * Takes one unit, or says there are none left.
     *
     * <p>The unit is <em>not</em> given back when the limit is hit: the
     * increment is what discovered it, and refunding here would let a user
     * over their limit hammer the endpoint with the counter never moving past
     * the ceiling. It is capped by the increment itself, not by the caller.
     */
    public Result<Void> consume(UserContext user, QuotaMetric metric) {
        QuotaSubject subject = QuotaSubject.of(user);
        int used = counters.increment(subject, metric);
        int limit = limits.dailyLimit(metric);

        if (used > limit) {
            // The subject and the numbers, never anything they wrote.
            log.info("Quota reached for {} on {}: {} of {}",
                    subject.type(), metric, used, limit);
            return Result.err(new PipelineError.QuotaExceeded(
                    metric.wireValue(), resetsAt()));
        }
        return Result.ok(null);
    }

    /**
     * Bolum 44.2: a failure the user did not get a document out of is given
     * back, whatever caused it.
     *
     * <p>The section splits user errors from system errors and refunds both,
     * which is the same rule stated twice — so this takes no reason. What it
     * must not do is refund a success, and the only caller is the failure
     * path.
     */
    public void refund(UserContext user, QuotaMetric metric) {
        counters.refund(QuotaSubject.of(user), metric);
    }

    /** What {@code GET /account/usage} and {@code capabilities} publish. */
    public Usage usage(UserContext user, QuotaMetric metric) {
        QuotaSubject subject = QuotaSubject.of(user);
        return new Usage(metric.wireValue(), counters.used(subject, metric),
                limits.dailyLimit(metric), resetsAt());
    }

    /**
     * The next UTC midnight, as an absolute instant (F-007).
     *
     * <p>An instant and not an hour: the client writes the sentence in the
     * user's own locale, and an hour without a date is ambiguous for anyone
     * asking near the boundary. UTC because {@code usage_counters.period} is a
     * timezone-less {@code DATE} and that is the only reading which keeps one
     * row meaning one day wherever the server runs.
     */
    private Instant resetsAt() {
        return counters.today().plusDays(1).atTime(LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC);
    }

    /**
     * @param resetsAt an absolute instant; the frontend renders the local text
     */
    public record Usage(String metric, int used, int limit, Instant resetsAt) {

        public int remaining() {
            return Math.max(0, limit - used);
        }
    }
}
