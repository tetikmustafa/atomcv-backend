package com.mustafatetik.atomcv.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.identity.domain.AuthMethod;
import com.mustafatetik.atomcv.identity.service.AccountDeletionService;
import com.mustafatetik.atomcv.identity.service.SessionStore;
import com.mustafatetik.atomcv.profile.seed.DevSeeder;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.security.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

    @Autowired
    private AccountDeletionService deletions;

    @Autowired
    private DevSeeder seeder;

    private UUID profileId;

    /**
     * <strong>This class is the only one that deletes the acting user, so it
     * is the only one that has to put them back.</strong> One context and one
     * database serve the whole suite, and {@code DevSeeder}'s golden profile
     * is seeded once at start-up — every class after this one inherits
     * whatever the last case here left behind.
     *
     * <p>Nothing enforced that until F-027. A request from a session pointing
     * at a deleted account used to be served as though the account were there,
     * so {@code SecondImportIT} went on getting its 409 out of a user who had
     * been deleted three classes earlier — passing on the very defect this
     * slice is about. The moment that request became a 401 the dependency
     * surfaced, which is the honest reading: the isolation was always missing
     * and the bug was hiding it.
     *
     * <p>Wiped rather than repaired, and in that order: {@code DevSeeder} is
     * idempotent by looking for a profile row, so an empty one left over would
     * make it skip and hand the next class a profile with nothing in it.
     */
    @AfterEach
    void leaveTheSeededProfileAsItWasFound() {
        jdbc.update("DELETE FROM users WHERE id = ?", LocalDevUser.DEV_USER_ID);
        jdbc.update("DELETE FROM usage_counters WHERE subject_id = ?",
                LocalDevUser.DEV_USER_ID.toString());
        localUser.ensureUserExists();
        seeder.run(null);
    }

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

    /**
     * <strong>F-027.</strong> The account is gone and a session is not, which
     * is what a revocation Redis could not carry out leaves behind — and what
     * {@code make dev} produces on every cookieless request, because the
     * stand-in signs in a row that has just been deleted.
     *
     * <p>Every one of these used to answer 500, and only these: the profile
     * endpoints create the profile row on first use and the insert broke
     * {@code profiles.user_id}. The generation list created nothing, so it
     * answered 200 with an empty page as though the account were fine — an
     * account that is not there, answering as though it were.
     */
    @Test
    void aSessionThatOutlivedItsAccountIsRefusedRatherThanBreakingAForeignKey()
            throws Exception {

        jdbc.update("DELETE FROM users WHERE id = ?", LocalDevUser.DEV_USER_ID);

        for (String path : List.of(
                "/api/v1/profile",
                "/api/v1/profile/sections",
                "/api/v1/profile/atoms",
                "/api/v1/profile/entries",
                "/api/v1/generations",
                "/api/v1/account/usage")) {

            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    /**
     * Bolum 35.6, which the same path was suspected of breaking: an account
     * that has never opened the editor reads as an empty profile and not as a
     * 404. It is the account being absent that refuses, not the profile.
     */
    @Test
    void anAccountWithoutAProfileStillReadsAsAnEmptyOne() throws Exception {
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevUser.DEV_USER_ID);

        mvc.perform(get("/api/v1/profile")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/profile/sections")).andExpect(status().isOk());
    }

    /**
     * A second press is not a failure, and it does not reach the endpoint
     * either.
     *
     * <p>This used to assert two 204s, and that was the dev stand-in talking:
     * a cookieless request is signed in as the dev user, so the second press
     * arrived authenticated. In a browser it cannot — the first response
     * cleared the {@code sid} — and since F-027 a session pointing at a
     * deleted account is refused rather than served. So the honest assertion
     * is the pair: the endpoint answers 401, and the idempotency Bolum 57.4
     * asks for is still underneath it, where it was always the service's.
     */
    @Test
    void pressingDeleteTwiceIsRefusedAtTheDoorAndIsStillNotAnError() throws Exception {
        mvc.perform(delete("/api/v1/account")).andExpect(status().isNoContent());

        mvc.perform(delete("/api/v1/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        assertThat(deletions.delete(UserContext.of(LocalDevUser.DEV_USER_ID)))
                .as("an account that is not there is false, not a failure")
                .isFalse();
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
