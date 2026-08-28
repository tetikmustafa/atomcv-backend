package com.mustafatetik.atomcv.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Bolum 40.5's window against a real Redis, at a clock that can be moved.
 *
 * <p>In {@code identity.ratelimit} so the limiter can be built by hand: the
 * clock <em>is</em> the thing under test, and the narrowest layer is fifteen
 * minutes wide. Waiting for the application's own clock would make half of
 * these cases untestable and the rest useless.
 *
 * <p>Every case takes a fresh subject, so nothing here depends on the order it
 * runs in or on a key another test left behind.
 */
class RateLimiterIT extends AbstractIntegrationTest {

    private static final Instant NOON = Instant.parse("2026-08-27T12:00:00Z");

    private static final Duration WINDOW = Duration.ofMinutes(15);

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void theWindowAdmitsExactlyItsLimitAndThenRefuses() {
        String subject = freshSubject();

        assertThat(limiterAt(NOON).check("test", subject, 3, WINDOW).allowed()).isTrue();
        assertThat(limiterAt(NOON).check("test", subject, 3, WINDOW).allowed()).isTrue();
        assertThat(limiterAt(NOON).check("test", subject, 3, WINDOW).allowed()).isTrue();

        assertThat(limiterAt(NOON).check("test", subject, 3, WINDOW).allowed()).isFalse();
    }

    /**
     * The probe for the random member.
     *
     * <p>Naming a member by its arrival alone would make these three requests
     * one member — {@code ZADD} is an upsert — and the second and third would
     * be admitted for free forever. The clock is deliberately frozen, which is
     * the case a wall clock would almost never produce and a busy second
     * produces constantly.
     */
    @Test
    void requestsArrivingInTheSameMillisecondEachTakeASlot() {
        String subject = freshSubject();
        RateLimiter limiter = limiterAt(NOON);

        assertThat(limiter.check("test", subject, 2, WINDOW).allowed()).isTrue();
        assertThat(limiter.check("test", subject, 2, WINDOW).allowed()).isTrue();

        assertThat(limiter.check("test", subject, 2, WINDOW).allowed()).isFalse();
    }

    /**
     * The probe that separates a sliding window from a fixed one.
     *
     * <p>A fixed window keyed on the quarter hour would have rolled over at
     * 12:15 and admitted this. The window slides, so a slot taken at 12:01 is
     * still held at 12:14.
     */
    @Test
    void aSlotIsStillHeldWhileItIsInsideTheWindow() {
        String subject = freshSubject();
        limiterAt(NOON.plusSeconds(60)).check("test", subject, 1, WINDOW);

        assertThat(limiterAt(NOON.plusSeconds(14 * 60)).check("test", subject, 1, WINDOW)
                .allowed()).isFalse();
    }

    @Test
    void aSlotIsGivenBackOnceItLeavesTheWindow() {
        String subject = freshSubject();
        limiterAt(NOON).check("test", subject, 1, WINDOW);

        assertThat(limiterAt(NOON.plus(WINDOW).plusSeconds(1)).check("test", subject, 1, WINDOW)
                .allowed()).isTrue();
    }

    /**
     * {@code Retry-After} is derived from this instant, so it has to be the
     * real one: the oldest held slot leaving the window, not the window's
     * width added to now.
     */
    @Test
    void aRefusalSaysWhenTheOldestSlotLeavesTheWindow() {
        String subject = freshSubject();
        limiterAt(NOON).check("test", subject, 1, WINDOW);

        RateLimitDecision refused =
                limiterAt(NOON.plusSeconds(300)).check("test", subject, 1, WINDOW);

        assertThat(refused.allowed()).isFalse();
        assertThat(refused.resetsAt()).isEqualTo(NOON.plus(WINDOW));
    }

    @Test
    void twoSubjectsAreTwoWindows() {
        String one = freshSubject();
        String other = freshSubject();
        limiterAt(NOON).check("test", one, 1, WINDOW);

        assertThat(limiterAt(NOON).check("test", other, 1, WINDOW).allowed()).isTrue();
    }

    @Test
    void twoLayersOverOneSubjectAreTwoWindows() {
        String subject = freshSubject();
        limiterAt(NOON).check("ip", subject, 1, WINDOW);

        assertThat(limiterAt(NOON).check("email", subject, 1, WINDOW).allowed()).isTrue();
    }

    /**
     * The claim that the limiter fails closed, probed against an outage rather
     * than assumed from the {@code catch}.
     *
     * <p>A template pointed at a closed port is the cheapest real outage: the
     * script cannot run, and what matters is that the answer is a refusal and
     * not an admission.
     */
    @Test
    void anUnreachableRedisRefusesRatherThanWavesEveryoneThrough() {
        RateLimitDecision decision = new RateLimiter(pointedAtNothing(), fixed(NOON))
                .check("test", freshSubject(), 3, WINDOW);

        assertThat(decision.allowed()).isFalse();
        // The backoff, not a window edge — there was no window to read.
        assertThat(decision.resetsAt()).isEqualTo(NOON.plusSeconds(60));
    }

    /** A closed port is the cheapest real outage. */
    private static StringRedisTemplate pointedAtNothing() {
        var factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", 1));
        factory.afterPropertiesSet();
        var template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    private RateLimiter limiterAt(Instant now) {
        return new RateLimiter(redis, fixed(now));
    }

    private static Clock fixed(Instant now) {
        return Clock.fixed(now, ZoneOffset.UTC);
    }

    private static String freshSubject() {
        return UUID.randomUUID().toString();
    }
}
