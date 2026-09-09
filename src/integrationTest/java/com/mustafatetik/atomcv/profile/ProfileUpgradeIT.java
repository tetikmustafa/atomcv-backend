package com.mustafatetik.atomcv.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.ingestion.normalization.NormalizedProfile;
import com.mustafatetik.atomcv.ingestion.service.ProfileWriter;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.repository.AnonymousProfiles;
import com.mustafatetik.atomcv.profile.service.ProfileUpgrade;
import com.mustafatetik.atomcv.profile.service.ProfileUpgradeService;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The anonymous profile becomes the account's (Adim 3.6, dilim 6).
 *
 * <p>What these cases hold is that it is the <em>same</em> profile and not a
 * copy of one. The rows are written with the ids they already had, which is
 * the whole reason the profile row can be adopted rather than rebuilt — and
 * the reason a field added to an atom next month arrives here without anybody
 * remembering to carry it across.
 */
class ProfileUpgradeIT extends AbstractIntegrationTest {

    @Autowired
    private ProfileUpgradeService upgrades;

    @Autowired
    private ProfileWriter writer;

    @Autowired
    private AnonymousProfiles anonymous;

    @Autowired
    private JdbcTemplate jdbc;

    private UserContext user;

    private AnonymousSessionId session;

    @BeforeEach
    void aStrangerWithAProfile() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, email_verified) VALUES (?, ?, true)",
                userId, userId + "@upgrade.test");
        user = UserContext.of(userId);
        session = AnonymousSessionId.of("upgrade-" + UUID.randomUUID());
        jdbc.update("DELETE FROM jobs WHERE user_id = ?", userId);
    }

    // -- the profile follows the person ------------------------------------

    @Test
    void theanonymousProfileBecomesTheAccountsWithItsOwnId() {
        Profile carried = anonymousProfile();

        assertThat(upgrades.upgrade(user, session)).isEqualTo(ProfileUpgrade.UPGRADED);

        assertThat(profileIdOf(user)).isEqualTo(carried.getId());
        assertThat(count("sections", carried.getId())).isEqualTo(2);
        assertThat(count("entries", carried.getId())).isEqualTo(2);
        assertThat(count("atoms", carried.getId())).isEqualTo(3);
        assertThat(count("atom_variants", carried.getId())).isEqualTo(5);
    }

    /**
     * <strong>Every id survives, and now it survives structurally.</strong> The
     * rows never move: the upgrade sets an owner on the head and the tree below
     * it was always addressed by {@code profile_id}. This used to guard against
     * a copier dropping a field added later; it now guards against anybody
     * reintroducing one.
     */
    @Test
    void everyRowKeepsTheIdItHadWhileItWasAnonymous() {
        Profile carried = anonymousProfile();
        List<UUID> sectionsBefore = idsIn("sections", carried.getId());
        List<UUID> atomsBefore = idsIn("atoms", carried.getId());

        upgrades.upgrade(user, session);

        assertThat(sectionsBefore).isNotEmpty();
        assertThat(idsIn("sections", carried.getId()))
                .containsExactlyInAnyOrderElementsOf(sectionsBefore);
        assertThat(idsIn("atoms", carried.getId()))
                .containsExactlyInAnyOrderElementsOf(atomsBefore);
    }

    /** And the header block with it — a profile row that lost the name would be new. */
    @Test
    void thecontactBlockAndTheLanguageComeAcross() {
        anonymousProfile();

        upgrades.upgrade(user, session);

        var row = jdbc.queryForMap(
                "SELECT contact::text AS contact, source_language FROM profiles WHERE user_id = ?",
                user.userId());
        assertThat((String) row.get("contact")).contains("Ada Lovelace");
        assertThat(row).containsEntry("source_language", "tr");
    }

    /**
     * The two jobs § 31.6.3 skipped while the profile had no rows. They are
     * possible now, and worth doing: the first generation from an account
     * should not be the degraded one.
     */
    @Test
    void theembeddingAndMeasurementSkippedByTheImportAreQueuedNow() {
        anonymousProfile();

        upgrades.upgrade(user, session);

        assertThat(jdbc.queryForList(
                "SELECT type FROM jobs WHERE user_id = ? ORDER BY type", String.class,
                user.userId())).containsExactly("embedding", "measurement");
    }

    /**
     * It stops expiring in the same statement that gives it an owner, and it
     * stops being reachable as anonymous.
     *
     * <p>{@code profiles_owner_xor_expiry} is what makes the first half
     * impossible to forget: a row that kept its expiry would have been swept out
     * from under the account that had just signed up for it.
     */
    @Test
    void theProfileStopsExpiringAndStopsBeingAnonymous() {
        Profile carried = anonymousProfile();

        upgrades.upgrade(user, session);

        assertThat(jdbc.queryForObject("SELECT expires_at FROM profiles WHERE id = ?",
                java.sql.Timestamp.class, carried.getId())).isNull();
        assertThat(anonymous.find(ProfileRef.ephemeral(session))).isEmpty();
    }

    /**
     * <strong>The CV follows the profile, and it is the reason somebody signs
     * up.</strong> A generation hangs off the profile, so its rows never move —
     * what changes is that they gain an owner, in the same transaction. Carrying
     * the profile and leaving the document behind would have deleted the very
     * thing the account was opened to keep: the anonymous profile's expiry is
     * what the sweep reads, and `generations.profile_id` cascades from it.
     */
    @Test
    void thegenerationsTheSessionMadeBecomeTheAccountsToo() {
        Profile carried = anonymousProfile();
        UUID generationId = aGenerationOf(carried.getId());

        assertThat(upgrades.upgrade(user, session)).isEqualTo(ProfileUpgrade.UPGRADED);

        assertThat(jdbc.queryForObject("SELECT user_id FROM generations WHERE id = ?",
                UUID.class, generationId)).isEqualTo(user.userId());
    }

    /**
     * And when the account keeps its own profile, the session's generations stay
     * with the profile they belong to — which expires. Adopting them would file a
     * CV made from one profile under another.
     */
    @Test
    void generationsAreLeftBehindWhenTheProfileIs() {
        UUID existing = emptyProfileRow();
        withASection(existing);
        Profile notCarried = anonymousProfile();
        UUID generationId = aGenerationOf(notCarried.getId());

        assertThat(upgrades.upgrade(user, session)).isEqualTo(ProfileUpgrade.KEPT_EXISTING);

        assertThat(jdbc.queryForObject("SELECT user_id FROM generations WHERE id = ?",
                UUID.class, generationId)).isNull();
    }

    // -- and when it does not ----------------------------------------------

    /** Most sign-ins. Nobody was carrying anything, and nothing is written. */
    @Test
    void asessionThatBuiltNothingUpgradesNothing() {
        assertThat(upgrades.upgrade(user, session)).isEqualTo(ProfileUpgrade.NONE);

        assertThat(count("profiles")).isZero();
    }

    /**
     * <strong>The account's own profile is not touched.</strong> Merging two
     * CVs is a product decision nobody has made, and overwriting months of
     * editing with two hours of it is the opposite of what design principle 8
     * asks for — so the anonymous one is left to its TTL and the person is
     * told.
     */
    @Test
    void anaccountThatAlreadyHasAProfileKeepsIt() {
        UUID existing = emptyProfileRow();
        withASection(existing);
        anonymousProfile();

        assertThat(upgrades.upgrade(user, session)).isEqualTo(ProfileUpgrade.KEPT_EXISTING);

        assertThat(profileIdOf(user)).isEqualTo(existing);
        assertThat(count("atoms", existing)).isZero();
        // Left alone rather than deleted: nothing was written, so nothing may
        // be thrown away either. It goes when its own window closes.
        assertThat(anonymous.find(ProfileRef.ephemeral(session))).isPresent();
    }

    /**
     * The narrow, silent case this check exists for.
     *
     * <p>{@code ProfileResolver.own} creates the row lazily, so signing in once
     * and opening the application is enough to have one. Before this, that
     * person's next anonymous CV was answered with {@code KEPT_EXISTING} and
     * the work was left to expire — an empty row outranking two hours of it.
     */
    @Test
    void anEmptyProfileRowIsNotAProfileAndIsOverwritten() {
        UUID placeholder = emptyProfileRow();
        Profile carried = anonymousProfile();

        assertThat(upgrades.upgrade(user, session)).isEqualTo(ProfileUpgrade.UPGRADED);

        assertThat(profileIdOf(user))
                .isEqualTo(carried.getId())
                .isNotEqualTo(placeholder);
        assertThat(count("atoms", carried.getId())).isPositive();
        // One row per user is a unique constraint, so the placeholder is gone
        // rather than orphaned beside the adopted one.
        assertThat(count("profiles")).isEqualTo(1);
    }

    // -- fixtures ----------------------------------------------------------

    /** A finished generation of that profile, owned by nobody. */
    private UUID aGenerationOf(UUID profileId) {
        return jdbc.queryForObject(
                "INSERT INTO generations (id, user_id, profile_id, options, selection_state,"
                        + " engine_version, status, created_at)"
                        + " VALUES (gen_random_uuid(), NULL, ?, '{}'::jsonb, '{}'::jsonb,"
                        + " '{}'::jsonb, 'completed', now()) RETURNING id",
                UUID.class, profileId);
    }

    /** What signing in once and opening the application leaves behind. */
    private UUID emptyProfileRow() {
        return jdbc.queryForObject("""
                INSERT INTO profiles (id, user_id, contact, preferences, source_language,
                                      enabled_languages, completeness, created_at, updated_at,
                                      version)
                VALUES (gen_random_uuid(), ?, '{}'::jsonb, '{}'::jsonb, 'en',
                        ARRAY['en'], 0, now(), now(), 0)
                RETURNING id""", UUID.class, user.userId());
    }

    /** Enough to make the row real: nothing below a section can exist without one. */
    private void withASection(UUID profileId) {
        jdbc.update("""
                INSERT INTO sections (profile_id, kind, title, display_order)
                VALUES (?, 'experience', 'Deneyim', 0)
                """, profileId);
    }

    private Profile anonymousProfile() {
        return writer.writeAnonymously(ProfileRef.ephemeral(session),
                Instant.now().plus(Duration.ofHours(2)), cv());
    }

    private UUID profileIdOf(UserContext owner) {
        return jdbc.queryForObject(
                "SELECT id FROM profiles WHERE user_id = ?", UUID.class, owner.userId());
    }

    private List<UUID> idsIn(String table, UUID profileId) {
        return jdbc.queryForList(
                "SELECT id FROM " + table + " WHERE profile_id = ?", UUID.class, profileId);
    }

    private int count(String table) {
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM " + table
                + " WHERE user_id = ?", Integer.class, user.userId());
        return rows == null ? 0 : rows;
    }

    private int count(String table, UUID profileId) {
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE profile_id = ?",
                Integer.class, profileId);
        return rows == null ? 0 : rows;
    }

    private static NormalizedProfile cv() {
        var bullet = new NormalizedProfile.NormalizedAtom(
                new RichContent(List.of(
                        Run.of("300 bin satiri "),
                        Run.of("Microsoft Fabric", Mark.TECHNOLOGY),
                        Run.of(" ile tasidim"))),
                RichContent.plain("Moved 300K rows with Microsoft Fabric"),
                List.of("microsoft-fabric"), List.of("300K rows"),
                List.of("Microsoft Fabric"), List.of("data-engineering"), (short) 0);

        var second = new NormalizedProfile.NormalizedAtom(
                RichContent.plain("Gece isini dort saatten kirk dakikaya indirdim"),
                RichContent.plain("Cut the nightly batch from four hours to forty minutes"),
                List.of("etl"), List.of(), List.of(), List.of(), (short) 1);

        var skill = new NormalizedProfile.NormalizedAtom(
                RichContent.plain("Python"), RichContent.EMPTY,
                List.of("python"), List.of(), List.of(), List.of(), (short) 0);

        var experience = new NormalizedProfile.NormalizedSection(
                SectionKind.EXPERIENCE, "Deneyim", (short) 0,
                List.of(new NormalizedProfile.NormalizedEntry(
                        "Data Engineer", "Brisa", "Istanbul",
                        YearMonth.of(2023, 9), null, (short) 0, List.of(bullet, second))));

        var skills = new NormalizedProfile.NormalizedSection(
                SectionKind.SKILLS, "Beceriler", (short) 1,
                List.of(new NormalizedProfile.NormalizedEntry(
                        "Diller", "", "", null, null, (short) 0, List.of(skill))));

        return new NormalizedProfile("tr",
                new Contact("Ada Lovelace", "ada@example.com", null, null, null, null,
                        "Istanbul"),
                List.of(experience, skills), List.of());
    }
}
