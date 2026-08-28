package com.mustafatetik.atomcv.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bolum 44.3's tightening, from the mark to the refusal.
 *
 * <p>The gap this closes is a rate, not a total. A daily ceiling of twenty says
 * nothing about spending twenty in four minutes, and the detector that would
 * notice runs every fifteen — so the burst it is meant to catch is over before
 * it looks. What the alarm now does is narrow that subject for six hours.
 *
 * <p>The hourly cap is set to one here so the second call is the refusal. The
 * default is two, and the number is configuration for the reason every other
 * limit is: it moves without a release.
 */
@SpringBootTest(properties = {
        // Inherited by hand, and it has to be: @SpringBootTest on a subclass
        // replaces the parent's attributes rather than adding to them, so
        // declaring `properties` here silently dropped all three of
        // AbstractIntegrationTest's switches -- including the job worker,
        // whose scheduler then claimed rows JobQueueIT was asserting on. It
        // read as an unrelated failure in another class.
        "atomcv.jobs.worker.enabled=false",
        "atomcv.anomaly.enabled=false",
        "atomcv.retention.enabled=false",
        "atomcv.quota.tightened-per-hour=1"})
class TighteningIT extends AbstractIntegrationTest {

    @Autowired
    private QuotaService quotas;

    @Autowired
    private TightenedSubjects tightened;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    private QuotaSubject subject;

    @BeforeEach
    void anUntouchedSubject() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, email_verified) VALUES (?, ?, true)",
                userId, userId + "@tightening.test");
        subject = QuotaSubject.of(UserContext.of(userId));
        // The limiter counts in Redis and the suite shares one; a key left by
        // another class would spend this subject's allowance before it starts.
        redis.delete(redis.keys("ratelimit:generation:*"));
    }

    @Test
    void anUnflaggedSubjectSpendsItsDailyAllowanceHoweverFast() {
        for (int i = 0; i < 5; i++) {
            assertThat(quotas.consume(subject, QuotaMetric.GENERATION).isErr())
                    .as("call %d of an ordinary day", i + 1)
                    .isFalse();
        }
    }

    @Test
    void aFlaggedSubjectIsHeldToTheHourlyCap() {
        tightened.tighten(subject.id(), Duration.ofHours(6));

        assertThat(quotas.consume(subject, QuotaMetric.GENERATION).isErr()).isFalse();

        var refused = quotas.consume(subject, QuotaMetric.GENERATION);
        assertThat(refused.isErr()).isTrue();
        assertThat(((Result.Err<Void>) refused).error())
                .isInstanceOf(PipelineError.QuotaExceeded.class);
    }

    /**
     * The refusal must not also cost a unit. Bolum 44.3 puts the brake in
     * front of the quota for this reason and the tightening sits in the same
     * place: a request that was never going to run has nothing to charge for.
     */
    @Test
    void aRefusalWhileTightenedSpendsNothing() {
        tightened.tighten(subject.id(), Duration.ofHours(6));
        quotas.consume(subject, QuotaMetric.GENERATION);

        int before = quotas.usage(subject, QuotaMetric.GENERATION).used();
        quotas.consume(subject, QuotaMetric.GENERATION);

        assertThat(quotas.usage(subject, QuotaMetric.GENERATION).used()).isEqualTo(before);
    }

    /** Released, and the next call goes through — the mark is not a sentence. */
    @Test
    void releasingASubjectRestoresIt() {
        tightened.tighten(subject.id(), Duration.ofHours(6));
        quotas.consume(subject, QuotaMetric.GENERATION);
        assertThat(quotas.consume(subject, QuotaMetric.GENERATION).isErr()).isTrue();

        tightened.release(subject.id());

        assertThat(quotas.consume(subject, QuotaMetric.GENERATION).isErr()).isFalse();
    }

    /** Extraction has its own low ceiling and no baseline; the mark does not reach it. */
    @Test
    void tighteningDoesNotTouchTheOtherMetric() {
        tightened.tighten(subject.id(), Duration.ofHours(6));

        assertThat(quotas.consume(subject, QuotaMetric.PROFILE_EXTRACT).isErr()).isFalse();
        assertThat(quotas.consume(subject, QuotaMetric.PROFILE_EXTRACT).isErr()).isFalse();
    }
}
