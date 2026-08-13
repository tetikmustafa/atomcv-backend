package com.mustafatetik.atomcv.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.VariantAuthor;
import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the profile entities against the real schema.
 *
 * <p>The context starting at all is the first assertion: {@code ddl-auto} is
 * {@code validate}, so a mapping that disagreed with the Flyway baseline would
 * fail before any test ran. The rest checks the parts validation cannot see —
 * that enums land in the schema's lowercase vocabulary, that content is real
 * JSONB in the documented shape, and that the skill arrays are queryable
 * {@code text[]} rather than serialized text.
 */
@SpringBootTest
@Testcontainers
class ProfileMappingIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private UUID profileId;

    @BeforeEach
    void createProfile() {
        UUID userId = jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
        profileId = jdbc.queryForObject(
                "INSERT INTO profiles (user_id) VALUES (?) RETURNING id", UUID.class, userId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users");
    }

    @Test
    void theProfileGraphRoundTrips() {
        var section = new Section(profileId, SectionKind.EXPERIENCE, "Experience", (short) 0);
        section.setLayout(SectionLayout.ENTRY_LIST);

        var entry = new Entry(profileId, section.getId(), "Backend Engineer", (short) 0);
        entry.setOrganization("Acme");
        entry.setStartDate(LocalDate.of(2023, 3, 1));
        entry.setRenderCost("classic:v1", 24.0);

        var atom = new Atom(profileId, section.getId(), entry.getId(), AtomKind.BULLET, (short) 0);
        atom.setSkills(List.of("go", "postgresql"));
        atom.setMetrics(List.of("300K+"));
        atom.setProperNouns(List.of("Acme"));

        var variant = new AtomVariant(profileId, atom.getId(), "en", RichContent.of(
                Run.of("Built "),
                Run.of("ETL", Mark.TECHNOLOGY),
                Run.of(" pipelines processing "),
                Run.of("300K+ rows", Mark.METRIC)));
        variant.setPrimary(true);
        variant.setTone(Tone.TECHNICAL);

        persist(section, entry, atom, variant);

        tx.executeWithoutResult(status -> {
            var loadedSection = em.find(Section.class, section.getId());
            assertThat(loadedSection.getKind()).isEqualTo(SectionKind.EXPERIENCE);
            assertThat(loadedSection.getLayout()).isEqualTo(SectionLayout.ENTRY_LIST);
            assertThat(loadedSection.isActive()).isTrue();
            assertThat(loadedSection.getVersion()).isZero();

            var loadedEntry = em.find(Entry.class, entry.getId());
            assertThat(loadedEntry.getStartDate()).isEqualTo(LocalDate.of(2023, 3, 1));
            assertThat(loadedEntry.isOngoing()).isTrue();
            assertThat(loadedEntry.getImportance()).isEqualTo(0.5f);
            assertThat(loadedEntry.getMinAtoms()).isEqualTo((short) 2);
            assertThat(loadedEntry.getRenderCosts()).containsExactly(Map.entry("classic:v1", 24.0));

            var loadedAtom = em.find(Atom.class, atom.getId());
            assertThat(loadedAtom.getSkills()).containsExactly("go", "postgresql");
            assertThat(loadedAtom.getMetrics()).containsExactly("300K+");
            assertThat(loadedAtom.getProperNouns()).containsExactly("Acme");
            assertThat(loadedAtom.getCreatedAt()).isNotNull();
            assertThat(loadedAtom.isSectionLevel()).isFalse();

            var loadedVariant = em.find(AtomVariant.class, variant.getId());
            assertThat(loadedVariant.getContent().plainText())
                    .isEqualTo("Built ETL pipelines processing 300K+ rows");
            assertThat(loadedVariant.getContent().runs().get(1).marks())
                    .containsExactly(Mark.TECHNOLOGY);
            assertThat(loadedVariant.getPlainText()).isEqualTo(loadedVariant.getContent().plainText());
            assertThat(loadedVariant.getContentHash()).isEqualTo(variant.getContentHash());
            assertThat(loadedVariant.getTone()).isEqualTo(Tone.TECHNICAL);
            assertThat(loadedVariant.getCreatedBy()).isEqualTo(VariantAuthor.USER);
            assertThat(loadedVariant.isPrimary()).isTrue();
        });
    }

    @Test
    void enumsLandInTheSchemaVocabulary() {
        var section = new Section(profileId, SectionKind.SOFT_SKILLS, "Strengths", (short) 1);
        section.setLayout(SectionLayout.INLINE_LIST);
        var atom = new Atom(profileId, section.getId(), null, AtomKind.ABOUT_PARAGRAPH, (short) 0);
        var variant = new AtomVariant(profileId, atom.getId(), "en", RichContent.plain("Mentoring"));

        persist(section, atom, variant);

        assertThat(scalar("SELECT kind FROM sections WHERE id = ?", section.getId()))
                .isEqualTo("soft_skills");
        assertThat(scalar("SELECT layout FROM sections WHERE id = ?", section.getId()))
                .isEqualTo("inline_list");
        assertThat(scalar("SELECT kind FROM atoms WHERE id = ?", atom.getId()))
                .isEqualTo("about_paragraph");
        assertThat(scalar("SELECT source FROM atoms WHERE id = ?", atom.getId()))
                .isEqualTo("manual");
        assertThat(scalar("SELECT created_by FROM atom_variants WHERE id = ?", variant.getId()))
                .isEqualTo("user");
        assertThat(scalar("SELECT tone FROM atom_variants WHERE id = ?", variant.getId()))
                .isNull();
    }

    @Test
    void contentIsStoredInTheDocumentedJsonbStructure() {
        var section = new Section(profileId, SectionKind.EXPERIENCE, "Experience", (short) 0);
        var atom = new Atom(profileId, section.getId(), null, AtomKind.BULLET, (short) 0);
        var variant = new AtomVariant(profileId, atom.getId(), "en", RichContent.of(
                Run.of("Built "),
                Run.of("ETL", Mark.TECHNOLOGY)));

        persist(section, atom, variant);

        // Reaching into the value with jsonb operators only works if the column
        // holds parsed JSON rather than a quoted string.
        assertThat(jdbc.queryForObject(
                "SELECT content->>'v' FROM atom_variants WHERE id = ?", String.class, variant.getId()))
                .isEqualTo("1");
        assertThat(jdbc.queryForObject(
                "SELECT jsonb_array_length(content->'runs') FROM atom_variants WHERE id = ?",
                Integer.class, variant.getId()))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT content->'runs'->1->>'t' FROM atom_variants WHERE id = ?",
                String.class, variant.getId()))
                .isEqualTo("ETL");
        assertThat(jdbc.queryForObject(
                "SELECT content->'runs'->1->'m'->>0 FROM atom_variants WHERE id = ?",
                String.class, variant.getId()))
                .isEqualTo("technology");
        assertThat(jdbc.queryForObject(
                "SELECT plain_text FROM atom_variants WHERE id = ?", String.class, variant.getId()))
                .isEqualTo("Built ETL");
    }

    @Test
    void skillsAreQueryableArraysNotSerializedText() {
        var section = new Section(profileId, SectionKind.SKILLS, "Skills", (short) 0);
        var atom = new Atom(profileId, section.getId(), null, AtomKind.SKILL, (short) 0);
        atom.setSkills(List.of("go", "postgresql", "kubernetes"));

        persist(section, atom);

        assertThat(jdbc.queryForObject(
                "SELECT array_length(skills, 1) FROM atoms WHERE id = ?", Integer.class, atom.getId()))
                .isEqualTo(3);
        assertThat(jdbc.queryForList(
                "SELECT id FROM atoms WHERE profile_id = ? AND skills @> ARRAY['go']",
                UUID.class, profileId))
                .containsExactly(atom.getId());
    }

    @Test
    void aStaleWriteIsRejectedByTheVersionColumn() {
        var section = new Section(profileId, SectionKind.PROJECTS, "Projects", (short) 0);
        persist(section);

        var detached = tx.execute(status -> {
            var loaded = em.find(Section.class, section.getId());
            em.detach(loaded);
            return loaded;
        });

        // Someone else saves first, in their own transaction.
        jdbc.update("UPDATE sections SET title = 'Selected projects', version = version + 1 WHERE id = ?",
                section.getId());

        detached.setTitle("Side projects");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            em.merge(detached);
            em.flush();
        })).isInstanceOfAny(OptimisticLockException.class, ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void aSectionLevelAtomNeedsNoEntry() {
        var section = new Section(profileId, SectionKind.LANGUAGES, "Languages", (short) 0);
        var atom = new Atom(profileId, section.getId(), null, AtomKind.LANGUAGE, (short) 0);

        persist(section, atom);

        assertThat(jdbc.queryForObject(
                "SELECT entry_id FROM atoms WHERE id = ?", UUID.class, atom.getId()))
                .isNull();
    }

    private void persist(Object... entities) {
        tx.executeWithoutResult(status -> {
            for (Object entity : entities) {
                em.persist(entity);
            }
        });
    }

    private String scalar(String sql, UUID id) {
        return jdbc.queryForObject(sql, String.class, id);
    }
}
