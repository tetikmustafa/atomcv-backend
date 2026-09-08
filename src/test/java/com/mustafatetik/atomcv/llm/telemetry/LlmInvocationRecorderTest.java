package com.mustafatetik.atomcv.llm.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Which figure reaches {@code cost_usd} (Bolum 27.4, 44.3).
 *
 * <p>The daily budget brake reads that column, so the question this answers is
 * not "does the arithmetic work" — {@link LlmPricingTest} owns that — but
 * "when two sources disagree, which one is written down". A broker that reports
 * what it took off the account knows something the table cannot: which of the
 * model's seven endpoints served the call, and whether a promotion was running.
 */
class LlmInvocationRecorderTest {

    private static final String MODEL = "openai/gpt-5.6-sol";

    /** 2 in, 10 out, per million — the table's figure for the same model. */
    private static final LlmPricing PRICING = new LlmPricing(Map.of(
            MODEL, new LlmPricing.ModelPrice(
                    new BigDecimal("2"), new BigDecimal("10"), new BigDecimal("0.2"))));

    @Test
    void thereportedCostIsWrittenRatherThanTheModelledOne() {
        var jdbc = mock(JdbcTemplate.class);

        recorderWith(jdbc, PRICING).record(event(MODEL, new BigDecimal("0.0432")));

        assertThat(costWrittenBy(jdbc))
                .as("the table would have said 0.070000 for these tokens")
                .isEqualByComparingTo("0.043200");
    }

    @Test
    void withoutAReportedCostTheTableAnswers() {
        var jdbc = mock(JdbcTemplate.class);

        recorderWith(jdbc, PRICING).record(event(MODEL, null));

        // 15,000 in at 2/M + 4,000 out at 10/M.
        assertThat(costWrittenBy(jdbc)).isEqualByComparingTo("0.070000");
    }

    /**
     * The counter behind F-015: a model nobody priced makes the whole cost
     * report low, and this is what says so before anyone reads a dashboard.
     */
    @Test
    void anunpricedModelWithNoReportedCostIsCounted() {
        var meters = new SimpleMeterRegistry();

        new LlmInvocationRecorder(mock(JdbcTemplate.class), new LlmPricing(Map.of()), meters)
                .record(event(MODEL, null));

        assertThat(meters.counter("llm.unpriced_calls", "model", MODEL).count()).isEqualTo(1.0);
    }

    /**
     * But not when the provider reported one. The figure written is right, and
     * a counter firing anyway would report a problem nobody has — the table
     * being silent about a model costs nothing when the bill is quoted.
     */
    @Test
    void anunpricedModelThatReportedItsCostIsNotCounted() {
        var meters = new SimpleMeterRegistry();

        new LlmInvocationRecorder(mock(JdbcTemplate.class), new LlmPricing(Map.of()), meters)
                .record(event(MODEL, new BigDecimal("0.0432")));

        assertThat(meters.counter("llm.unpriced_calls", "model", MODEL).count()).isZero();
    }

    private static LlmInvocationRecorder recorderWith(JdbcTemplate jdbc, LlmPricing pricing) {
        return new LlmInvocationRecorder(jdbc, pricing, new SimpleMeterRegistry());
    }

    /**
     * The eighth parameter of the INSERT is {@code cost_usd}.
     *
     * <p>Read off the recorded invocation rather than through a captor: the
     * statement's parameters are varargs, and a captor in that position matches
     * one argument where thirteen were passed.
     */
    private static BigDecimal costWrittenBy(JdbcTemplate jdbc) {
        Object[] arguments = mockingDetails(jdbc).getInvocations().iterator().next()
                .getArguments();
        // [sql, p1 .. p13], or [sql, Object[13]] depending on how the varargs
        // were recorded. Both say the same thing; neither is worth asserting.
        Object[] parameters = arguments.length == 2 && arguments[1] instanceof Object[] passed
                ? passed
                : java.util.Arrays.copyOfRange(arguments, 1, arguments.length);
        return (BigDecimal) parameters[7];
    }

    private static LlmInvocationEvent event(String model, BigDecimal reportedCost) {
        return new LlmInvocationEvent(
                "profile_extraction", "v1", "openrouter", model,
                LlmInvocationEvent.Outcome.SUCCESS,
                15_000, 4_000, 0, 1_200L, Instant.parse("2026-09-08T19:00:00Z"),
                null, null, reportedCost);
    }
}
