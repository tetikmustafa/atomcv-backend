package com.mustafatetik.atomcv.shared.config;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What {@code deploy.sh} calls after health and before traffic.
 *
 * <p><strong>Not a public endpoint, and three separate things keep it that
 * way</strong>: {@link Hidden} keeps it out of the OpenAPI document so no
 * generated client ever sees it, nginx denies the exact path, and it takes no
 * input and answers nothing. It is an operational lever with a URL, not part
 * of the API.
 *
 * <p><strong>What it warms, and what warms itself.</strong> The costs a first
 * request pays here are the two I/O paths: a JDBC connection out of a cold
 * pool, and a Redis round trip — the session lookup every authenticated
 * request begins with. Both are paid once and then never again, and paying
 * them on somebody's sign-in is the cold start this exists to avoid.
 *
 * <p>The expensive warm-ups are not here on purpose. XeLaTeX compiles a
 * minimal document inside its own container at startup and the embedding
 * server loads its weights before answering {@code /health} — neither is
 * reachable from a request thread, and a warm-up that pretended to cover them
 * would report success for work it never did.
 *
 * <p><strong>It cannot fail the deploy.</strong> A cold pool is slower, not
 * broken, and {@code deploy.sh} already decided the release was good when
 * {@code /actuator/health} answered. Turning a warm-up into a second health
 * check would roll back a working version over a slow one.
 */
@RestController
@Hidden
public class WarmupController {

    private static final Logger log = LoggerFactory.getLogger(WarmupController.class);

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    WarmupController(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    @GetMapping("/api/v1/warmup")
    public ResponseEntity<Void> warmUp() {
        touch("database", () -> jdbc.queryForObject("SELECT 1", Integer.class));
        touch("redis", () -> redis.getConnectionFactory() == null
                ? null
                : redis.getConnectionFactory().getConnection().ping());
        return ResponseEntity.noContent().build();
    }

    /**
     * Logs what could not be reached and carries on. The counterpart never
     * gets a sentence of its own: a warm-up that logged its successes would
     * write two lines on every deploy that said nothing.
     */
    private void touch(String what, Callable<?> work) {
        try {
            work.call();
        } catch (Exception unreachable) {
            // The class name, never a payload -- there is none here, and the
            // habit is absolute rule 4's.
            log.warn("Warm-up could not reach {}: {}", what,
                    unreachable.getClass().getSimpleName());
        }
    }
}
