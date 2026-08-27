package com.mustafatetik.atomcv.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.ingestion.normalization.NormalizedProfile;
import com.mustafatetik.atomcv.ingestion.service.EphemeralProfileWriter;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import com.mustafatetik.atomcv.profile.service.EphemeralProfile;
import com.mustafatetik.atomcv.profile.service.EphemeralProfileStore;
import com.mustafatetik.atomcv.profile.service.ProfileUpgrade;
import com.mustafatetik.atomcv.profile.service.ProfileUpgradeService;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
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
    private EphemeralProfileWriter ephemeral;

    @Autowired
    private EphemeralProfileStore store;

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
        EphemeralProfile anonymous = anonymousProfile();

        assertThat(upgrades.upgrade(user, session)).isEqualTo(ProfileUpgrade.UPGRADED);

        assertThat(profileIdOf(user)).isEqualTo(anonymous.profileId());
        assertThat(count("sections", anonymous.profileId())).isEqualTo(2);
        assertThat(count("entries", anonymous.profileId())).isEqualTo(2);
        assertThat(count("atoms", anonymous.profileId())).isEqualTo(3);
        assertThat(count("atom_variants", anonymous.profileId())).isEqualTo(5);
    }

    /**
     * <strong>Every id survives.</strong> Rebuilding the rows under fresh ids
     * would look identical from outside and would mean a copier naming each
     * field it carried — the failure mode being a field added later that the
     * copier silently drops.
     */
    @Test
    void everyRowKeepsTheIdItHadWhileItWasAnonymous() {
        EphemeralProfile anonymous = anonymousProfile();

        upgrades.upgrade(user, session);

        assertThat(idsIn("sections", anonymous.profileId()))
                .containsExactlyInAnyOrderElementsOf(
                        anonymous.sections().stream().map(Section::getId).toList());
        assertThat(idsIn("atoms", anonymous.profileId()))
                .containsExactlyInAnyOrderElementsOf(
                        anonymous.atoms().stream().map(Atom::getId).toList());
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

    /** Nothing is left in Redis: the document was moved, not duplicated. */
    @Test
    void theanonymousDocumentIsGoneAfterwards() {
        anonymousProfile();

        upgrades.upgrade(user, session);

        assertThat(store.find(ProfileRef.ephemeral(session))).isEmpty();
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
        UUID existing = jdbc.queryForObject("""
                INSERT INTO profiles (id, user_id, contact, preferences, source_language,
                                      enabled_languages, completeness, created_at, updated_at,
                                      version)
                VALUES (gen_random_uuid(), ?, '{}'::jsonb, '{}'::jsonb, 'en',
                        ARRAY['en'], 0, now(), now(), 0)
                RETURNING id""", UUID.class, user.userId());
        anonymousProfile();

        assertThat(upgrades.upgrade(user, session)).isEqualTo(ProfileUpgrade.KEPT_EXISTING);

        assertThat(profileIdOf(user)).isEqualTo(existing);
        assertThat(count("atoms", existing)).isZero();
        // Left alone rather than discarded: nothing was written, so nothing
        // may be thrown away either.
        assertThat(store.find(ProfileRef.ephemeral(session))).isPresent();
    }

    // -- fixtures ----------------------------------------------------------

    private EphemeralProfile anonymousProfile() {
        return ephemeral.write(ProfileRef.ephemeral(session), cv());
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
