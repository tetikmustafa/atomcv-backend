package com.mustafatetik.atomcv.profile.seed;

import com.mustafatetik.atomcv.profile.domain.AtomTag;
import com.mustafatetik.atomcv.profile.domain.Tag;
import com.mustafatetik.atomcv.profile.domain.TagSource;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Something to look at after {@code make dev} (XI-A.3 Adim 1.9).
 *
 * <p>Seeds the golden fixture the tests use, so what a developer sees locally
 * is the same profile the guards are written against — a seed that drifted
 * from the fixtures would make "it looks fine locally" mean nothing.
 *
 * <p><strong>Local profile only</strong>, and idempotent: it does nothing at
 * all if the developer's profile already has content, because overwriting what
 * someone typed in to try the app out is exactly design principle 8's failure.
 */
@Component
@Profile("local")
@Order(100)
public class DevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);

    /** Which fixture to seed. Any of {@link GoldenProfileReader#NAMES}. */
    private final String fixture;

    @PersistenceContext
    private EntityManager em;

    DevSeeder(@Value("${atomcv.dev.seed-profile:senior_backend_tr}") String fixture) {
        this.fixture = fixture;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long existing = em.createQuery(
                        "SELECT count(p) FROM Profile p WHERE p.userId = :owner", Long.class)
                .setParameter("owner", LocalDevUser.DEV_USER_ID)
                .getSingleResult();
        if (existing > 0) {
            log.debug("The local profile already exists; not seeding");
            return;
        }

        GoldenProfile golden = GoldenProfileReader.read(fixture, LocalDevUser.DEV_USER_ID);
        em.persist(golden.profile());
        golden.sections().forEach(em::persist);
        golden.entries().forEach(em::persist);
        golden.atoms().forEach(em::persist);
        golden.variants().forEach(em::persist);
        int tags = seedTags(golden);

        // Counts, never content (absolute rule 4) — even for a fixture, since
        // the same line would print a real profile if the guard above changed.
        log.info("Seeded the {} fixture: {} sections, {} entries, {} atoms, {} tags",
                fixture, golden.sections().size(), golden.entries().size(),
                golden.atoms().size(), tags);
    }

    /**
     * The tags, as the two rows production stores them in.
     *
     * <p><strong>Not a detail of the fixture.</strong> Bolum 19.1 gives the tag
     * term a quarter of the raw score, and a seeded profile with no tag rows
     * makes that quarter structurally zero for every generation run locally —
     * which is a scorer behaving differently on a developer's machine than in
     * production, and the hardest kind of difference to notice.
     *
     * <p>One {@code tags} row per distinct label and one {@code atom_tags} row
     * per wearer, which is the shape the unique index on
     * {@code (profile_id, label)} requires.
     *
     * @return how many atom-to-tag links were written
     */
    private int seedTags(GoldenProfile golden) {
        var byLabel = new java.util.LinkedHashMap<String, Tag>();
        int links = 0;
        for (var tagged : golden.tagsByAtom().entrySet()) {
            for (String label : tagged.getValue()) {
                Tag tag = byLabel.computeIfAbsent(label, fresh -> {
                    var created = new Tag(golden.profile().getId(), fresh);
                    em.persist(created);
                    return created;
                });
                em.persist(new AtomTag(tagged.getKey(), tag.getId(), TagSource.AUTO));
                links++;
            }
        }
        return links;
    }
}
