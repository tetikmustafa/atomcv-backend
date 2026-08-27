package com.mustafatetik.atomcv.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.ingestion.normalization.NormalizedProfile;
import com.mustafatetik.atomcv.ingestion.service.ProfileWriter;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A normalised CV against a real database.
 *
 * <p>What only shows up here: that the rows are actually written under a scope
 * the profile owns, that {@code YearMonth} survives the {@code DATE} column,
 * that the run structure survives its JSONB converter, and that the whole tree
 * arrives or none of it does. A unit test with mocked repositories would have
 * asserted the calls and none of the four.
 */
class ProfileWriterIT extends AbstractIntegrationTest {

    @Autowired
    private ProfileWriter writer;

    @Autowired
    private JdbcTemplate jdbc;

    private UserContext user;

    @BeforeEach
    void freshUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, email_verified) VALUES (?, ?, true)",
                userId, userId + "@writer.test");
        user = UserContext.of(userId);
    }

    @Test
    void aWholeCvArrivesAsRowsUnderTheUsersOwnProfile() {
        var profile = writer.write(user, cv());

        assertThat(count("sections", profile.getId())).isEqualTo(2);
        assertThat(count("entries", profile.getId())).isEqualTo(2);
        assertThat(count("atoms", profile.getId())).isEqualTo(3);
        // Two wordings each for the two Turkish bullets, one for the skill —
        // which is English already and gets no second copy of itself.
        assertThat(count("atom_variants", profile.getId())).isEqualTo(5);
        assertThat(profile.getOwnerId()).isEqualTo(user.userId());
    }

    @Test
    void theContactBlockAndTheLanguageReachTheProfileRow() {
        var profile = writer.write(user, cv());

        assertThat(profile.getContact().name()).isEqualTo("Ada Lovelace");
        assertThat(profile.getSourceLanguage()).isEqualTo("tr");
    }

    /**
     * The column is a {@code DATE} and a CV gives months. The day is a storage
     * artefact — what matters is that the month survives the round trip rather
     * than arriving as a null nobody notices.
     */
    @Test
    void aMonthSurvivesTheDateColumnAsTheFirstOfThatMonth() {
        var profile = writer.write(user, cv());

        var start = jdbc.queryForObject(
                "SELECT start_date FROM entries WHERE profile_id = ? AND title = ?",
                java.sql.Date.class, profile.getId(), "Data Engineer");
        assertThat(start.toLocalDate()).isEqualTo(java.time.LocalDate.of(2023, 9, 1));

        // Still there means null, and null is not a failure to read a date.
        var end = jdbc.queryForObject(
                "SELECT end_date FROM entries WHERE profile_id = ? AND title = ?",
                java.sql.Date.class, profile.getId(), "Data Engineer");
        assertThat(end).isNull();
    }

    @Test
    void theRunStructureSurvivesItsJsonbColumn() {
        var profile = writer.write(user, cv());

        var content = jdbc.queryForObject("""
                SELECT v.content::text FROM atom_variants v
                JOIN atoms a ON a.id = v.atom_id
                WHERE v.profile_id = ? AND v.is_primary = true AND a.kind = 'bullet'
                LIMIT 1""", String.class, profile.getId());

        assertThat(content).contains("Microsoft Fabric").contains("technology");
    }

    /**
     * The plain text and the hash are stored beside the runs, and both are
     * computed over the text — Bolum 16.2, so that re-marking a sentence does
     * not invalidate the embedding keyed on it.
     */
    @Test
    void thePlainTextAndTheHashAreWrittenAlongsideTheRuns() {
        var profile = writer.write(user, cv());

        var row = jdbc.queryForMap("""
                SELECT plain_text, content_hash FROM atom_variants
                WHERE profile_id = ? AND is_primary = true
                ORDER BY plain_text LIMIT 1""", profile.getId());

        assertThat((String) row.get("plain_text")).isNotBlank();
        assertThat((String) row.get("content_hash")).hasSize(64);
    }

    /** Bolum 13: the section a bullet sits under says what kind of atom it is. */
    @Test
    void anAtomTakesItsKindFromTheSectionAboveIt() {
        var profile = writer.write(user, cv());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM atoms WHERE profile_id = ? AND kind = 'skill'",
                Integer.class, profile.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM atoms WHERE profile_id = ? AND kind = 'bullet'",
                Integer.class, profile.getId())).isEqualTo(2);
    }

    /**
     * Bolum 14.1: where an atom came from decides what may be done to it, and
     * an imported bullet is the person's own sentence.
     */
    @Test
    void everyImportedAtomSaysItCameFromAnUpload() {
        var profile = writer.write(user, cv());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM atoms WHERE profile_id = ? AND source <> 'cv_upload'",
                Integer.class, profile.getId())).isZero();
    }

    /**
     * Bolum 21 reads an absent English variant as "the source is the English",
     * so an English-only atom gets one row rather than two copies to keep in
     * step.
     */
    @Test
    void anAtomWithNoSecondWordingGetsOneVariant() {
        var profile = writer.write(user, cv());

        var wordings = jdbc.queryForObject("""
                SELECT count(*) FROM atom_variants v
                JOIN atoms a ON a.id = v.atom_id
                WHERE v.profile_id = ? AND a.kind = 'skill'""",
                Integer.class, profile.getId());

        assertThat(wordings).isEqualTo(1);
    }

    @Test
    void oneUsersImportIsInvisibleToAnother() {
        var mine = writer.write(user, cv());

        UUID otherId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, email_verified) VALUES (?, ?, true)",
                otherId, otherId + "@writer.test");
        var theirs = writer.write(UserContext.of(otherId), cv());

        assertThat(theirs.getId()).isNotEqualTo(mine.getId());
        assertThat(count("atoms", mine.getId())).isEqualTo(3);
        assertThat(count("atoms", theirs.getId())).isEqualTo(3);
    }

    // -- fixtures ----------------------------------------------------------

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
