package com.mustafatetik.atomcv.llm.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every call, written down with what it cost (Bolum 27.5, Bolum 44.3).
 *
 * <p>{@code ProviderChain} has published these events since Adim 2.2 and
 * nothing listened, so {@code llm_invocations} was empty and every cost figure
 * in the system was zero — including the one Bolum 44.3's budget brake reads,
 * which meant the brake could never fire. This is the listener.
 *
 * <p><strong>Failures are recorded too.</strong> Bolum 27.5 counts every call,
 * and a provider that answers with a schema error still bills for the tokens
 * it produced. A cost report that only counted successes would understate a
 * bad day exactly when it mattered.
 *
 * <p><strong>Its own transaction, and it never fails the caller.</strong>
 * Telemetry that can roll back a generation is worse than telemetry that is
 * occasionally missing: the user asked for a CV, not for a row in a cost
 * table.
 *
 * <p>{@code user_id} is left null. The event does not carry one — the chain is
 * called from phases that hold a {@code UserContext} but do not pass it down —
 * and inventing an owner would be worse than an honest gap. The daily total,
 * which is what the brake needs, does not depend on it; per-user cost
 * attribution does, and arrives when the id is plumbed through.
 */
@Component
public class LlmInvocationRecorder {

    private static final Logger log = LoggerFactory.getLogger(LlmInvocationRecorder.class);

    private final JdbcTemplate jdbc;
    private final LlmPricing pricing;
    private final MeterRegistry meters;

    LlmInvocationRecorder(JdbcTemplate jdbc, LlmPricing pricing, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.pricing = pricing;
        this.meters = meters;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LlmInvocationEvent event) {
        BigDecimal cost = pricing.costOf(event.model(),
                event.inputTokens(), event.outputTokens(), event.cachedTokens());

        if (!pricing.knows(event.model())) {
            // Counted rather than logged per call: a model nobody priced makes
            // the whole cost report low, and a rising number here says the
            // price table has fallen behind the chain.
            meters.counter("llm.unpriced_calls", "model", event.model()).increment();
        }
        meters.counter("llm.calls", "outcome", event.outcome().name().toLowerCase(
                java.util.Locale.ROOT)).increment();

        try {
            jdbc.update("""
                    INSERT INTO llm_invocations (
                        prompt_id, prompt_version, provider, model,
                        input_tokens, output_tokens, cached_tokens,
                        cost_usd, latency_ms, outcome, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    event.promptId(), event.promptVersion(), event.provider(), event.model(),
                    event.inputTokens(), event.outputTokens(), event.cachedTokens(),
                    cost, event.latencyMs(), event.outcome().name().toLowerCase(
                            java.util.Locale.ROOT),
                    java.sql.Timestamp.from(event.occurredAt()));
        } catch (RuntimeException unwritten) {
            // Never the prompt or the answer, and never the caller's problem
            // (absolute rule 4).
            log.error("Could not record an LLM invocation for {}: {}",
                    event.promptId(), unwritten.toString());
        }
    }
}
