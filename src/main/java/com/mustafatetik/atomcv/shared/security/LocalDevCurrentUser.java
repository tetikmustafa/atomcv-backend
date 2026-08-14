package com.mustafatetik.atomcv.shared.security;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A stand-in acting user for local development, until identity lands in
 * Stage 3 (XI-A.6).
 *
 * <p>Adim 1.2 needs endpoints, endpoints need a {@link UserContext}, and a
 * {@code ProfileRef} cannot be produced without one. The alternatives were
 * worse: picking the user from a request header is an authentication bypass
 * the moment it reaches production, and waiting for identity would block the
 * rest of Stage 1, all of which reads profile data.
 *
 * <p><strong>It cannot exist outside the {@code local} profile.</strong> There
 * is no fallback bean, so a production start-up fails loudly the moment
 * something needs a user rather than quietly serving one user's data to
 * everyone. That failure is the point, and it ends when the real
 * implementation arrives — at which point this class is deleted whole.
 */
@Component
@Profile("local")
public class LocalDevCurrentUser implements CurrentUser, ApplicationRunner {

    /** Fixed so that local data survives a restart and seeds stay meaningful. */
    public static final UUID DEV_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String DEV_EMAIL = "dev@localhost";

    private static final Logger log = LoggerFactory.getLogger(LocalDevCurrentUser.class);

    private final JdbcTemplate jdbc;

    LocalDevCurrentUser(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserContext require() {
        return new UserContext(DEV_USER_ID, UserRole.USER);
    }

    /**
     * The row has to exist: every profile is a foreign key away from it.
     * Written with JDBC because identity has no entity yet — and when it does,
     * this whole class goes.
     *
     * <p>Runs as an {@link ApplicationRunner} so it happens after Flyway rather
     * than racing it.
     */
    @Override
    public void run(ApplicationArguments args) {
        ensureUserExists();
    }

    /**
     * Idempotent, and public so that a test which clears {@code users} can put
     * the row back without repeating the statement.
     */
    public void ensureUserExists() {
        int inserted = jdbc.update("""
                INSERT INTO users (id, email, display_name, role, email_verified)
                VALUES (?, ?, 'Local developer', 'USER', true)
                ON CONFLICT (id) DO NOTHING
                """, DEV_USER_ID, DEV_EMAIL);
        if (inserted > 0) {
            log.info("Created the local development user");
        }
    }
}
