package com.mustafatetik.atomcv.profile;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bolum 52.2: a profile load must stay inside six queries no matter how large
 * the profile is. The failure this guards against is not slow — it is invisible
 * until production, because the code that causes it looks ordinary.
 */
class ProfileAssemblerIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private ProfileAssembler assembler;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager em;

    private UUID userId;
    private ProfileRef profile;

    @BeforeEach
    void createProfile() {
        userId = newUser();
        profile = profileFor(userId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users");
    }

    @Test
    void loadingALargeProfileStaysInsideSixQueries() {
        seed(profile, 4, 5, 3);

        var statistics = statistics();
        statistics.clear();
        var tree = assembler.load(profile);

        assertThat(tree.atomCount()).isEqualTo(4 * 5 * 3);
        // The lower bound matters as much as the upper one: statistics that
        // silently returned zero would make this pass without measuring.
        assertThat(statistics.getPrepareStatementCount())
                .as("a profile load must not grow a query per section, entry or atom")
                .isBetween(4L, 6L);
    }

    @Test
    void theQueryCountDoesNotGrowWithTheProfile() {
        seed(profile, 2, 2, 1);
        var statistics = statistics();
        statistics.clear();
        assembler.load(profile);
        long small = statistics.getPrepareStatementCount();

        var bigger = profileFor(newUser());
        seed(bigger, 6, 6, 4);
        statistics.clear();
        assembler.load(bigger);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(small);
    }

    @Test
    void aProfileNeverSeesAnotherProfilesRows() {
        seed(profile, 2, 2, 2);
        var neighbour = profileFor(newUser());
        seed(neighbour, 3, 3, 3);

        var mine = assembler.load(profile);
        var theirs = assembler.load(neighbour);

        assertThat(mine.sections()).hasSize(2);
        assertThat(mine.atomCount()).isEqualTo(2 * 2 * 2);
        assertThat(theirs.sections()).hasSize(3);
        assertThat(theirs.atomCount()).isEqualTo(3 * 3 * 3);
    }

    @Test
    void anEmptyProfileLoadsToAnEmptyTree() {
        var tree = assembler.load(profile);

        assertThat(tree.profileId()).isEqualTo(profile.id());
        assertThat(tree.sections()).isEmpty();
    }

    @Test
    void theTreeCarriesTheOrderTheSchemaIndexesFor() {
        tx.executeWithoutResult(status -> {
            // Inserted out of order on purpose: display order decides, not insertion.
            em.persist(new Section(profile.id(), SectionKind.PROJECTS, "Projects", (short) 2));
            em.persist(new Section(profile.id(), SectionKind.EXPERIENCE, "Experience", (short) 0));
            em.persist(new Section(profile.id(), SectionKind.EDUCATION, "Education", (short) 1));
        });

        var tree = assembler.load(profile);

        assertThat(tree.sections()).extracting(node -> node.section().getKind())
                .containsExactly(SectionKind.EXPERIENCE, SectionKind.EDUCATION, SectionKind.PROJECTS);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private UUID newUser() {
        return jdbc.queryForObject("INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
    }

    private ProfileRef profileFor(UUID owner) {
        UUID profileId = jdbc.queryForObject("INSERT INTO profiles (user_id) VALUES (?) RETURNING id",
                UUID.class, owner);
        return ProfileRef.persistent(UserContext.of(owner), profileId, owner);
    }

    /** One profile with {@code sections} sections, each with entries and atoms. */
    private void seed(ProfileRef target, int sections, int entriesPerSection, int atomsPerEntry) {
        tx.executeWithoutResult(status -> {
            for (int s = 0; s < sections; s++) {
                var section = new Section(target.id(), SectionKind.EXPERIENCE, "Experience " + s, (short) s);
                em.persist(section);
                for (int e = 0; e < entriesPerSection; e++) {
                    var entry = new Entry(target.id(), section.getId(), "Entry " + e, (short) e);
                    em.persist(entry);
                    for (int a = 0; a < atomsPerEntry; a++) {
                        var atom = new Atom(target.id(), section.getId(), entry.getId(),
                                AtomKind.BULLET, (short) a);
                        em.persist(atom);
                        em.persist(new AtomVariant(target.id(), atom.getId(), "en",
                                RichContent.plain("Bullet " + s + "." + e + "." + a)));
                    }
                }
            }
        });
    }
}
