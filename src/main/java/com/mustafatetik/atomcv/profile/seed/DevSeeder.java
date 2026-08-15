package com.mustafatetik.atomcv.profile.seed;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.shared.security.LocalDevCurrentUser;
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
                .setParameter("owner", LocalDevCurrentUser.DEV_USER_ID)
                .getSingleResult();
        if (existing > 0) {
            log.debug("The local profile already exists; not seeding");
            return;
        }

        GoldenProfile golden = GoldenProfileReader.read(fixture, LocalDevCurrentUser.DEV_USER_ID);
        em.persist(golden.profile());
        golden.sections().forEach(em::persist);
        golden.entries().forEach(em::persist);
        golden.atoms().forEach(em::persist);
        golden.variants().forEach(em::persist);

        // Counts, never content (absolute rule 4) — even for a fixture, since
        // the same line would print a real profile if the guard above changed.
        log.info("Seeded the {} fixture: {} sections, {} entries, {} atoms",
                fixture, golden.sections().size(), golden.entries().size(),
                golden.atoms().size());
    }
}
