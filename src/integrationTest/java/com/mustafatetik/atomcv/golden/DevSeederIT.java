package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.profile.seed.DevSeeder;
import com.mustafatetik.atomcv.shared.security.LocalDevCurrentUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The local seed (XI-A.3 Adim 1.9).
 *
 * <p>Two things are worth a test rather than a look: that it puts a whole
 * profile in — the fixture's rows go into four tables with foreign keys
 * between them — and that it never touches one that is already there
 * (design principle 8).
 */
class DevSeederIT extends AbstractIntegrationTest {

    @Autowired
    private DevSeeder seeder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevCurrentUser localUser;

    @BeforeEach
    void startFromAnEmptyProfile() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevCurrentUser.DEV_USER_ID);
    }

    @Test
    void seedingPutsAWholeProfileIn() {
        seeder.run(null);

        UUID profileId = jdbc.queryForObject("SELECT id FROM profiles WHERE user_id = ?",
                UUID.class, LocalDevCurrentUser.DEV_USER_ID);
        assertThat(rows("sections", profileId)).isPositive();
        assertThat(rows("entries", profileId)).isPositive();
        assertThat(rows("atoms", profileId)).isPositive();
        assertThat(rows("atom_variants", profileId)).isPositive();
    }

    @Test
    void seedingTwiceChangesNothing() {
        seeder.run(null);
        UUID profileId = jdbc.queryForObject("SELECT id FROM profiles WHERE user_id = ?",
                UUID.class, LocalDevCurrentUser.DEV_USER_ID);
        int atoms = rows("atoms", profileId);

        seeder.run(null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM profiles WHERE user_id = ?",
                Integer.class, LocalDevCurrentUser.DEV_USER_ID)).isEqualTo(1);
        assertThat(rows("atoms", profileId)).isEqualTo(atoms);
    }

    @Test
    void aProfileThatAlreadyExistsIsLeftAlone() {
        // Someone typed their own CV in locally. Seeding must not replace it.
        UUID profileId = jdbc.queryForObject(
                "INSERT INTO profiles (user_id, headline) VALUES (?, 'Mine') RETURNING id",
                UUID.class, LocalDevCurrentUser.DEV_USER_ID);

        seeder.run(null);

        assertThat(jdbc.queryForObject("SELECT headline FROM profiles WHERE id = ?",
                String.class, profileId)).isEqualTo("Mine");
        assertThat(rows("sections", profileId)).isZero();
    }

    private int rows(String table, UUID profileId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE profile_id = ?",
                Integer.class, profileId);
        return count == null ? 0 : count;
    }
}
