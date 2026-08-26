package com.mustafatetik.atomcv.shared.security;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The row every local profile and every generation hangs off, and nothing more.
 *
 * <p>This was {@code LocalDevUser} and it was a {@code CurrentUser}: a
 * fixed acting user, standing in until identity arrived. Identity arrived in
 * Adim 3.3 and took that half — {@code CurrentUser} is now backed by a real
 * session. What is left is the seed.
 *
 * <p><strong>It was not deleted whole, as its predecessor said it would be.</strong>
 * Sessions landed before any way to start one did: OAuth is the next slice and
 * the magic link the one after. Deleting the seed here would leave {@code make
 * dev} with a database that has no user in it and a browser that can be nobody.
 * The row is what {@code identity.service.LocalDevSessions} signs in, and both
 * go when a real login exists.
 *
 * <p><strong>It cannot exist outside the {@code local} profile.</strong> A
 * production start-up has no such bean, so nothing can fall back to a shared
 * user.
 */
@Component
@Profile("local")
@Order(0)
public class LocalDevUser implements ApplicationRunner {

    /** Fixed so that local data survives a restart and seeds stay meaningful. */
    public static final UUID DEV_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String DEV_EMAIL = "dev@localhost";

    private static final Logger log = LoggerFactory.getLogger(LocalDevUser.class);

    private final JdbcTemplate jdbc;

    LocalDevUser(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The row has to exist: every profile is a foreign key away from it.
     * Written with JDBC because identity has no entity yet — Adim 3.3's first
     * slice needs no read of {@code users}, and inventing one to seed a row
     * would be the tail wagging the dog.
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
