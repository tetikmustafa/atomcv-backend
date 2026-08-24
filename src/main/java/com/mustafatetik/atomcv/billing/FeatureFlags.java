package com.mustafatetik.atomcv.billing;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The emergency brake (Bolum 44.3).
 *
 * <p>{@code feature_flags} has been in {@code V1} from the start; this reads
 * and writes it. A flag that has never been set is <strong>on</strong>: the
 * brake is something an operator applies, and a deployment with an empty table
 * must serve rather than refuse everything.
 *
 * <p><strong>The brake stops generation, not access.</strong> Bolum 44.3 calls
 * that critical and it is: a user whose generations are paused can still open
 * their profile, edit it and export it. Losing a day's work to a cost spike
 * would be a worse outcome than the spike.
 *
 * <p>Read straight from the database on every check rather than cached. The
 * flag exists to be flipped in the middle of an incident, and a cache is a
 * delay between the decision and the effect measured in whatever the TTL is.
 * One indexed primary-key lookup per generation is not the cost worth
 * optimising here.
 */
@Service
public class FeatureFlags {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlags.class);

    /** Bolum 44.3's brake: no new generations are accepted. */
    public static final String NEW_GENERATIONS = "generation.new_requests";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    FeatureFlags(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @return true when nothing has turned it off */
    public boolean isEnabled(String key) {
        List<Boolean> found = jdbc.query(
                "SELECT enabled FROM feature_flags WHERE key = ?",
                (row, index) -> row.getBoolean(1), key);
        return found.stream().findFirst().orElse(true);
    }

    @Transactional
    public void disable(String key) {
        set(key, false);
        log.warn("Feature flag {} disabled", key);
    }

    @Transactional
    public void enable(String key) {
        set(key, true);
        log.info("Feature flag {} enabled", key);
    }

    private void set(String key, boolean enabled) {
        jdbc.update("""
                INSERT INTO feature_flags (key, enabled, updated_at) VALUES (?, ?, ?)
                ON CONFLICT (key) DO UPDATE SET enabled = EXCLUDED.enabled,
                                                updated_at = EXCLUDED.updated_at
                """, key, enabled, java.sql.Timestamp.from(clock.instant()));
    }
}
