package com.mustafatetik.atomcv.db;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies that the Flyway baseline produces the schema the architecture
 * document describes, and that the tenant integrity constraints added on top
 * of it actually hold. A mismatch between the denormalized {@code profile_id}
 * and the parent row would be a silent cross-tenant leak, so it is asserted
 * here rather than trusted.
 */
class SchemaMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        // Everything user-owned cascades from users. llm_invocations survives
        // user deletion by design, so it is cleared explicitly.
        jdbc.update("DELETE FROM llm_invocations");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void migrationCreatesEveryDocumentedTable() {
        var tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains(
                "users", "oauth_identities", "magic_link_tokens", "email_suppressions",
                "email_preferences", "profiles", "sections", "entries", "atoms",
                "atom_variants", "tags", "atom_tags", "template_customizations",
                "generations", "generation_feedback", "support_grants", "applications",
                "jobs", "llm_invocations", "usage_counters", "feature_flags");
    }

    @Test
    void requiredExtensionsAreInstalled() {
        var extensions = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);

        assertThat(extensions).contains("vector", "citext", "pg_trgm");
    }

    @Test
    void embeddingColumnMatchesTheModelDimension() {
        // BGE-M3 produces 1024-dimensional vectors.
        var type = jdbc.queryForObject(
                "SELECT format_type(a.atttypid, a.atttypmod) FROM pg_attribute a "
                        + "WHERE a.attrelid = 'atoms'::regclass AND a.attname = 'embedding'",
                String.class);

        assertThat(type).isEqualTo("vector(1024)");
    }

    @Test
    void entryCannotReferenceSectionFromAnotherProfile() {
        var victim = newProfile("victim@example.com");
        var attacker = newProfile("attacker@example.com");
        var victimSection = newSection(victim);

        assertThatThrownBy(() -> insertEntry(attacker, victimSection))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void atomCannotReferenceEntryFromAnotherProfile() {
        var victim = newProfile("victim@example.com");
        var attacker = newProfile("attacker@example.com");
        var victimEntry = newEntry(victim);
        var attackerSection = newSection(attacker);

        // The section reference is consistent; only the entry crosses tenants.
        assertThatThrownBy(() -> insertAtom(attacker, attackerSection, victimEntry))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void variantCannotReferenceAtomFromAnotherProfile() {
        var victim = newProfile("victim@example.com");
        var attacker = newProfile("attacker@example.com");
        var victimAtom = newAtom(victim);

        assertThatThrownBy(() -> insertVariant(attacker, victimAtom))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void atomAttachedDirectlyToSectionIsAllowed() {
        var profile = newProfile("solo@example.com");
        var section = newSection(profile);

        // entry_id IS NULL, so the composite foreign key is not enforced.
        assertThatCode(() -> insertAtom(profile, section, null)).doesNotThrowAnyException();
    }

    @Test
    void deletingUserRemovesAllProfileData() {
        var user = newUser("leaving@example.com");
        var profile = newProfile(user);
        var section = newSection(profile);
        var entry = newEntry(profile, section);
        var atom = newAtom(profile, section, entry);
        insertVariant(profile, atom);

        jdbc.update("DELETE FROM users WHERE id = ?", user);

        assertThat(countAll("profiles")).isZero();
        assertThat(countAll("sections")).isZero();
        assertThat(countAll("entries")).isZero();
        assertThat(countAll("atoms")).isZero();
        assertThat(countAll("atom_variants")).isZero();
    }

    @Test
    void deletingUserKeepsCostHistoryButSeversTheLink() {
        var user = newUser("costly@example.com");
        jdbc.update("""
                INSERT INTO llm_invocations
                    (user_id, prompt_id, prompt_version, provider, model, cost_usd, outcome)
                VALUES (?, 'job_analysis', 'v1', 'gemini', 'test-model', 0.000420, 'success')
                """, user);

        jdbc.update("DELETE FROM users WHERE id = ?", user);

        assertThat(countAll("llm_invocations")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM llm_invocations WHERE user_id IS NULL", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT sum(cost_usd) FROM llm_invocations", Double.class))
                .isEqualTo(0.000420);
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private int countAll(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private UUID newUser(String email) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", id, email);
        return id;
    }

    private UUID newProfile(String email) {
        return newProfile(newUser(email));
    }

    private UUID newProfile(UUID userId) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO profiles (id, user_id) VALUES (?, ?)", id, userId);
        return id;
    }

    private UUID newSection(UUID profileId) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sections (id, profile_id, kind, title, display_order)
                VALUES (?, ?, 'experience', 'Experience', 1)
                """, id, profileId);
        return id;
    }

    private UUID newEntry(UUID profileId) {
        return newEntry(profileId, newSection(profileId));
    }

    private UUID newEntry(UUID profileId, UUID sectionId) {
        var id = UUID.randomUUID();
        insertEntry(profileId, sectionId, id);
        return id;
    }

    private void insertEntry(UUID profileId, UUID sectionId) {
        insertEntry(profileId, sectionId, UUID.randomUUID());
    }

    private void insertEntry(UUID profileId, UUID sectionId, UUID id) {
        jdbc.update("""
                INSERT INTO entries (id, profile_id, section_id, title, display_order)
                VALUES (?, ?, ?, 'Backend Engineer', 1)
                """, id, profileId, sectionId);
    }

    private UUID newAtom(UUID profileId) {
        return newAtom(profileId, newSection(profileId), null);
    }

    private UUID newAtom(UUID profileId, UUID sectionId, UUID entryId) {
        var id = UUID.randomUUID();
        insertAtom(profileId, sectionId, entryId, id);
        return id;
    }

    private void insertAtom(UUID profileId, UUID sectionId, UUID entryId) {
        insertAtom(profileId, sectionId, entryId, UUID.randomUUID());
    }

    private void insertAtom(UUID profileId, UUID sectionId, UUID entryId, UUID id) {
        jdbc.update("""
                INSERT INTO atoms (id, profile_id, section_id, entry_id, kind, display_order)
                VALUES (?, ?, ?, ?, 'bullet', 1)
                """, id, profileId, sectionId, entryId);
    }

    private void insertVariant(UUID profileId, UUID atomId) {
        jdbc.update("""
                INSERT INTO atom_variants
                    (profile_id, atom_id, content, plain_text, content_hash)
                VALUES (?, ?, ?::jsonb, 'sample text', 'hash')
                """, profileId, atomId, "{\"v\":1,\"runs\":[]}");
    }
}
