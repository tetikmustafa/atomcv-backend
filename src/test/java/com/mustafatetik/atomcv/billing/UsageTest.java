package com.mustafatetik.atomcv.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.billing.QuotaService.Usage;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The two numbers a client reads, and why there are two of them (F-012).
 *
 * <p>The counter behind this counts attempts — a refused request keeps its
 * unit, which is what stops a user who is already over from hammering the
 * endpoint. So the raw count runs past the limit, and the frontend was handed
 * "26 of 20" to render.
 */
class UsageTest {

    private static final Instant RESETS = Instant.parse("2026-08-26T00:00:00Z");

    @Test
    void belowTheLimitTheTwoNumbersAgree() {
        var usage = Usage.of("generation", 3, 20, RESETS);

        assertThat(usage.used()).isEqualTo(3);
        assertThat(usage.attempted()).isEqualTo(3);
        assertThat(usage.remaining()).isEqualTo(17);
    }

    @Test
    void refusedRequestsRaiseAttemptedAndLeaveConsumptionAtTheLimit() {
        // Six refusals on top of a limit of twenty. `used`/`limit` stays a
        // pair that can be printed; the refusals are still on the wire, in the
        // field that means refusals.
        var usage = Usage.of("generation", 26, 20, RESETS);

        assertThat(usage.used()).isEqualTo(20);
        assertThat(usage.attempted()).isEqualTo(26);
        assertThat(usage.remaining()).isZero();
    }

    @Test
    void exactlyAtTheLimitNothingIsLeftAndNothingIsOverstated() {
        var usage = Usage.of("generation", 20, 20, RESETS);

        assertThat(usage.used()).isEqualTo(20);
        assertThat(usage.attempted()).isEqualTo(20);
        assertThat(usage.remaining()).isZero();
    }

    @Test
    void aconsumptionAboveTheLimitCannotBeConstructed() {
        // The clamp is in one factory and the invariant is what keeps it
        // there: a second construction site that forgot to clamp would put
        // "26 of 20" back on the screen without a test going red.
        assertThatThrownBy(() -> new Usage("generation", 26, 26, 20, 0, RESETS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remainingCannotDisagreeWithWhatWasSpent() {
        assertThatThrownBy(() -> new Usage("generation", 3, 3, 20, 5, RESETS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consumptionCannotExceedTheAttemptsThatProducedIt() {
        assertThatThrownBy(() -> new Usage("generation", 5, 3, 20, 15, RESETS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
