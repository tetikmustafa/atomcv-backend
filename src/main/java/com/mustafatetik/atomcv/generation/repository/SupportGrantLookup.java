package com.mustafatetik.atomcv.generation.repository;

import com.mustafatetik.atomcv.generation.domain.SupportGrant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * A grant found without an acting user, because finding it is how the acting
 * user is discovered (Bolum 48.4, 41.2).
 *
 * <p><strong>Deliberately unscoped, and separate from
 * {@link SupportGrantRepository} because of it</strong> — the shape
 * {@code jobs.queue} already uses for the same reason. Absolute rule 3 exists to
 * stop a request reaching another user's row. This is not a request: it is the
 * offline support reader, which starts from a generation id somebody was given
 * and has nobody to scope by until the grant tells it whose CV this is.
 *
 * <p><strong>The grant is the credential, and this is the only unscoped step.</strong>
 * Everything the reader does afterwards goes through the ordinary scoped
 * repositories, acting as the owner the grant names — which is exactly what the
 * person consented to for forty-eight hours. An admin role would not have
 * helped: {@code ProfileRefTest} asserts a role buys no reach, because ownership
 * is settled in the repository layer and support access goes through a granted
 * {@code support_grant}.
 *
 * <p>Separate type rather than an unscoped method on the scoped repository, so
 * that nothing reaches this by autocompleting inside a controller.
 */
@Repository
public class SupportGrantLookup {

    private final SupportGrantJpaRepository jpa;

    SupportGrantLookup(SupportGrantJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * The newest grant on a generation, whoever owns it.
     *
     * <p>Newest rather than open: whether it is open is the reader's decision to
     * report, and "there is a grant and it ran out" is a different answer from
     * "nobody ever granted anything". Both are answers somebody debugging needs.
     */
    @Transactional(readOnly = true)
    public Optional<SupportGrant> newestFor(UUID generationId) {
        return jpa.findFirstByGenerationIdOrderByGrantedAtDesc(generationId);
    }

    /** Writes the access stamp back, and nothing else about the grant. */
    @Transactional
    public void save(SupportGrant grant) {
        jpa.save(grant);
    }
}
