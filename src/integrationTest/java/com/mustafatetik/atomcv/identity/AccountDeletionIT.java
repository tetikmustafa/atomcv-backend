package com.mustafatetik.atomcv.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.identity.domain.AuthMethod;
import com.mustafatetik.atomcv.identity.service.SessionStore;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import com.mustafatetik.atomcv.shared.security.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code DELETE /account}: the right to be forgotten, checked against the
 * schema rather than against a list (Bolum 57.4).
 *
 * <p><strong>The table list is read out of the database.</strong> A test that
 * named the tables would pass for ever after somebody added the one it does
 * not know about — and "hesap silme her yerden siliyor" is on the MVP release
 * checklist precisely because that is the failure nobody notices. So this asks
 * {@code information_schema} which tables carry a {@code user_id} or a
 * {@code profile_id} and requires every one of them to be empty of this
 * person, which makes a new table covered on the day it is created.
 */
@AutoConfigureMockMvc
class AccountDeletionIT extends AbstractIntegrationTest {

    /**
     * The two rows that survive on purpose (Bolum 57.4), each for its own
     * reason. Cost history keeps its row with the link cut; a suppressed
     * address belongs to the address rather than to the account, and dropping
     * it would let the product mail somewhere that had already bounced.
     */
    private static final List<String> SURVIVES = List.of("llm_invocations");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevUser localUser;

    @Autowired
    private SessionStore sessions;

    private UUID profileId;

    @BeforeEach
    void anaccountWithSomethingInIt() {
        // From scratch, by the same cascade this test is about: a profile left
        // over from the case before collides on its own unique keys.
        jdbc.update("DELETE FROM users WHERE id = ?", LocalDevUser.DEV_USER_ID);
        jdbc.update("DELETE FROM usage_counters WHERE subject_id = ?",
                LocalDevUser.DEV_USER_ID.toString());
        localUser.ensureUserExists();
        profileId = seedEverything();
    }

    /**
     * <strong>The release checklist's line, as an assertion.</strong> Every
     * table that can hold this person holds nothing of theirs afterwards.
     */
    @Test
    void deletingAnAccountEmptiesEveryTableThatHeldIt() throws Exception {
        assertThat(rowsOwnedByTheUser()).isNotEmpty();

        mvc.perform(delete("/api/v1/account")).andExpect(status().isNoContent());

        assertThat(rowsOwnedByTheUser())
                .as("every table with a user_id or a profile_id is empty of this account")
                .isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?",
                Integer.class, LocalDevUser.DEV_USER_ID)).isZero();
    }

    /**
     * Bolum 57.4, and the schema says so in its own comment: a month's spend
     * is not personal data once it points at nobody, so the row stays and the
     * link is cut.
     */
    @Test
    void costHistorySurvivesWithTheLinkCut() throws Exception {
        jdbc.update("""
                INSERT INTO llm_invocations
                    (user_id, prompt_id, prompt_version, provider, model,
                     input_tokens, output_tokens, cost_usd, latency_ms, outcome)
                VALUES (?, 'job_analysis', 'v1', 'fake', 'fake-model', 10, 20, 0.0, 5, 'success')
                """, LocalDevUser.DEV_USER_ID);

        mvc.perform(delete("/api/v1/account")).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM llm_invocations WHERE user_id IS NULL",
                Integer.class)).isPositive();
    }

    /**
     * <strong>Redis is the half the cascade cannot reach.</strong> A deleted
     * account whose cookie still resolved would be an account that was not
     * deleted — every request it made would find a session pointing at a row
     * that is gone.
     */
    @Test
    void everySessionSignedIntoTheAccountStopsWorking() throws Exception {
        var session = sessions.create(
                LocalDevUser.DEV_USER_ID, UserRole.USER, AuthMethod.MAGIC_LINK);

        mvc.perform(delete("/api/v1/account")).andExpect(status().isNoContent());

        assertThat(sessions.find(session.id())).isEmpty();
    }

    /** A second press is the same answer as the first, not a failure. */
    @Test
    void deletingTwiceIsNotAnError() throws Exception {
        mvc.perform(delete("/api/v1/account")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/account")).andExpect(status().isNoContent());
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    /**
     * Every table this person can appear in, with a row in it. A cascade that
     * misses one is only visible if something was there to miss.
     */
    private UUID seedEverything() {
        UUID userId = LocalDevUser.DEV_USER_ID;
        UUID profile = jdbc.queryForObject("""
                INSERT INTO profiles (user_id, source_language)
                VALUES (?, 'en')
                ON CONFLICT (user_id) DO UPDATE SET source_language = 'en'
                RETURNING id
                """, UUID.class, userId);

        UUID section = jdbc.queryForObject("""
                INSERT INTO sections (profile_id, kind, title, display_order)
                VALUES (?, 'experience', 'Experience', 0) RETURNING id
                """, UUID.class, profile);
        UUID entry = jdbc.queryForObject("""
                INSERT INTO entries (profile_id, section_id, title, display_order)
                VALUES (?, ?, 'Engineer', 0) RETURNING id
                """, UUID.class, profile, section);
        UUID atom = jdbc.queryForObject("""
                INSERT INTO atoms (profile_id, section_id, entry_id, kind, display_order)
                VALUES (?, ?, ?, 'bullet', 0) RETURNING id
                """, UUID.class, profile, section, entry);
        jdbc.update("""
                INSERT INTO atom_variants
                    (atom_id, profile_id, language, content, plain_text, content_hash)
                VALUES (?, ?, 'en', '{"runs":[]}'::jsonb, 'Built things', 'abc123')
                """, atom, profile);
        UUID tag = jdbc.queryForObject("""
                INSERT INTO tags (profile_id, label) VALUES (?, 'leadership') RETURNING id
                """, UUID.class, profile);
        jdbc.update("INSERT INTO atom_tags (atom_id, tag_id) VALUES (?, ?)", atom, tag);

        UUID generation = jdbc.queryForObject("""
                INSERT INTO generations
                    (user_id, profile_id, options, selection_state, engine_version, status)
                VALUES (?, ?, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, 'completed')
                RETURNING id
                """, UUID.class, userId, profile);
        jdbc.update("""
                INSERT INTO generation_feedback (generation_id, user_id, rating)
                VALUES (?, ?, 1)
                """, generation, userId);
        jdbc.update("""
                INSERT INTO support_grants (user_id, generation_id, expires_at)
                VALUES (?, ?, now() + interval '48 hours')
                """, userId, generation);
        jdbc.update("""
                INSERT INTO applications (user_id, generation_id, company, position)
                VALUES (?, ?, 'Acme', 'Engineer')
                """, userId, generation);
        jdbc.update("""
                INSERT INTO jobs (type, user_id, status, payload)
                VALUES ('generation', ?, 'queued', '{}'::jsonb)
                """, userId);
        // Keyed by (subject_type, subject_id) and not by a foreign key, which
        // is the whole reason this table is worth seeding here.
        jdbc.update("""
                INSERT INTO usage_counters (subject_type, subject_id, metric, period, count)
                VALUES ('user', ?, 'generation', CURRENT_DATE, 1)
                ON CONFLICT DO NOTHING
                """, userId.toString());
        jdbc.update("""
                INSERT INTO email_preferences (user_id) VALUES (?)
                ON CONFLICT DO NOTHING
                """, userId);
        jdbc.update("""
                INSERT INTO template_customizations
                    (profile_id, name, base_template_id, template_version, params)
                VALUES (?, 'mine', 'classic', 1, '{}'::jsonb)
                """, profile);
        return profile;
    }

    /**
     * Every row, anywhere, that still points at this account or its profile.
     *
     * @return one entry per table that still holds something, so a failure
     *         names the table rather than only counting
     */
    private List<String> rowsOwnedByTheUser() {
        List<String> holding = new java.util.ArrayList<>();
        for (String table : tablesWithColumn("user_id")) {
            if (SURVIVES.contains(table)) {
                continue;
            }
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM " + table + " WHERE user_id = ?",
                    Integer.class, LocalDevUser.DEV_USER_ID);
            if (count != null && count > 0) {
                holding.add(table + "=" + count);
            }
        }
        for (String table : tablesWithColumn("profile_id")) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM " + table + " WHERE profile_id = ?",
                    Integer.class, profileId);
            if (count != null && count > 0) {
                holding.add(table + "=" + count);
            }
        }
        // And the tables that name a person without a foreign key to them.
        // usage_counters is keyed by (subject_type, subject_id) because the
        // subject may be an address or an anonymous session, so no cascade
        // reaches it and nothing but this sweep would notice.
        for (String table : tablesWithColumn("subject_id")) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM " + table
                            + " WHERE subject_type = 'user' AND subject_id = ?",
                    Integer.class, LocalDevUser.DEV_USER_ID.toString());
            if (count != null && count > 0) {
                holding.add(table + "=" + count);
            }
        }
        return holding;
    }

    private List<String> tablesWithColumn(String column) {
        return jdbc.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name = ?
                ORDER BY table_name
                """, String.class, column);
    }
}
