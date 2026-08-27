package com.mustafatetik.atomcv.billing;

import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import io.swagger.v3.oas.annotations.media.Schema;
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
    /**
     * <p><strong>The subject and not a user</strong> (Adim 3.6). An account and
     * an anonymous address are both things an allowance belongs to, and an
     * overload for each read well until a test tried to stub one: with two
     * signatures, {@code any()} resolves to neither. One method that takes the
     * thing that actually varies is both clearer and testable.
     */
    public Result<Void> consume(QuotaSubject subject, QuotaMetric metric) {
        int used = counters.increment(subject, metric);
        int limit = limits.dailyLimit(metric, subject.type());

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
    public void refund(QuotaSubject subject, QuotaMetric metric) {
        counters.refund(subject, metric);
    }

    /** What {@code GET /account/usage} and {@code capabilities} publish. */
    public Usage usage(QuotaSubject subject, QuotaMetric metric) {
        return Usage.of(metric.wireValue(), counters.used(subject, metric),
                limits.dailyLimit(metric, subject.type()), resetsAt());
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
     * Today's allowance as a client reads it (F-012).
     *
     * <p><strong>Two numbers, because there are two facts.</strong> The
     * counter behind this counts <em>attempts</em>: {@link #consume} increments
     * before it checks and does not give the unit back when the limit is hit,
     * which is what stops a user who is already over from hammering the
     * endpoint with the number never moving. So the raw counter runs past the
     * limit, and a single field called {@code used} carrying 26 against a
     * limit of 20 reads as a broken screen rather than as a refusal.
     *
     * <p>{@code used} is therefore consumption — never more than
     * {@code limit}, so {@code used}/{@code limit} is a pair that can be
     * printed — and {@code attempted} is the raw count, refusals included.
     * Clamping alone would have been the server misreporting itself; the
     * second field is what keeps it honest.
     *
     * @param used      what was actually spent, {@code 0..limit}
     * @param attempted every request that took a unit, refused ones included.
     *                  Equal to {@code used} until the limit is reached, above
     *                  it afterwards.
     * @param remaining {@code limit - used}, and never negative
     * @param resetsAt  an absolute instant; the frontend renders the local text
     */
    @Schema(description = "One metric's allowance for today")
    public record Usage(
            String metric,

            @Schema(description = "What was actually spent. Never above `limit`, "
                    + "so `used`/`limit` is a pair that can be printed as it is.")
            int used,

            @Schema(description = "Every request that took a unit, refusals included. "
                    + "Equal to `used` until the limit is reached, above it afterwards.")
            int attempted,

            int limit,

            @Schema(description = "`limit - used`, never negative")
            int remaining,

            @Schema(description = "When the counters roll over, as an absolute instant. "
                    + "The boundary is UTC midnight.")
            Instant resetsAt) {

        static Usage of(String metric, int attempted, int limit, Instant resetsAt) {
            int used = Math.min(attempted, limit);
            return new Usage(metric, used, attempted, limit, limit - used, resetsAt);
        }

        public Usage {
            if (used > limit || used > attempted || remaining != limit - used) {
                throw new IllegalArgumentException(
                        "used is consumption: 0..limit, and remaining is what is left of it");
            }
        }
    }
}
