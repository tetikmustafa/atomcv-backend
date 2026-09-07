package com.mustafatetik.atomcv.llm.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.llm.gateway.JsonSchema;
import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bolum 27.5's {@code llm_invocations.user_id}, from the event to the column.
 *
 * <p>The column existed from V1 and nothing had ever written it: the daily
 * total the budget brake reads does not need it, so nothing failed, and "what
 * did this user cost" simply had no answer. It is not a question that can be
 * answered later either — an invocation not attributed when it happened cannot
 * be attributed afterwards, which is why this landed before the rows piled up.
 *
 * <p>Asserted end to end rather than on the event alone: the event carrying a
 * value it never binds into the statement is exactly the failure that would
 * otherwise reach production silently.
 */
class LlmInvocationAttributionIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anAttributedCallNamesTheUserItWasFor() {
        UUID user = someone();

        events.publishEvent(LlmInvocationEvent.succeeded(
                request(user), answer(), Instant.now()));

        assertThat(userIdOn("job_analysis", user)).isEqualTo(user);
    }

    /**
     * And an anonymous one leaves it NULL rather than inventing an owner.
     * Bolum 31.6.3's uploads have no account behind them, and a session id in
     * a column that means "which account spent this" would be worse than a
     * blank.
     */
    @Test
    void anUnattributedCallLeavesTheColumnEmpty() {
        events.publishEvent(LlmInvocationEvent.succeeded(
                request(null), answer(), Instant.now()));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM llm_invocations
                WHERE prompt_id = 'profile_extraction' AND user_id IS NULL
                """, Integer.class)).isPositive();
    }

    /**
     * And {@code job_id}, which had the same shape of hole. Nothing wrote it
     * either, so a billed call could only be tied to the work that caused it by
     * matching timestamps — which is how a four-page CV was found to have been
     * paid for twice with a single job row to show for it.
     */
    @Test
    void acallMadeByAJobNamesTheJobItWasFor() {
        UUID user = someone();
        UUID job = queuedJob(user);

        events.publishEvent(LlmInvocationEvent.succeeded(
                request(user).inJob(job), answer(), Instant.now()));

        assertThat(jdbc.queryForObject(
                "SELECT job_id FROM llm_invocations WHERE user_id = ? AND job_id = ?",
                UUID.class, user, job)).isEqualTo(job);
    }

    /** A call nobody queued leaves it NULL rather than borrowing a job. */
    @Test
    void acallOutsideTheQueueLeavesTheJobEmpty() {
        UUID user = someone();

        events.publishEvent(LlmInvocationEvent.succeeded(
                request(user), answer(), Instant.now()));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM llm_invocations WHERE user_id = ? AND job_id IS NULL",
                Integer.class, user)).isPositive();
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private UUID queuedJob(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO jobs (id, type, user_id, payload, status) "
                + "VALUES (?, 'generation', ?, '{}'::jsonb, 'queued')", id, owner);
        return id;
    }

    private UUID someone() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, email_verified) VALUES (?, ?, true)",
                id, id + "@attribution.test");
        return id;
    }

    private UUID userIdOn(String promptId, UUID user) {
        return jdbc.queryForObject(
                "SELECT user_id FROM llm_invocations WHERE prompt_id = ? AND user_id = ?",
                UUID.class, promptId, user);
    }

    private static StructuredRequest<String> request(UUID userId) {
        var base = new StructuredRequest<>(
                userId == null ? "profile_extraction" : "job_analysis", "v1",
                "system", "a posting",
                new JsonSchema("s", JSON.createObjectNode().put("type", "object")),
                String.class, ModelTier.CHEAP, Duration.ofSeconds(30));
        return userId == null ? base : base.forUser(userId);
    }

    private static LlmResponse<String> answer() {
        return new LlmResponse<>("answer", "fake", "fake-model", 10, 5, 0, 12);
    }
}
