package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.repository.AnonymousGenerations;
import com.mustafatetik.atomcv.profile.service.AnonymousProfileAdopted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The CVs a session made follow it into the account (Adim 3.6, Bolum 9).
 *
 * <p><strong>Why this listens instead of being called.</strong> The profile
 * module publishes {@link AnonymousProfileAdopted} and knows nothing about
 * generations; this module already depends on it, so the dependency runs one way
 * and ArchUnit stays satisfied. It also means the next thing that hangs off an
 * anonymous profile is added by writing a second listener rather than by editing
 * the upgrade.
 *
 * <p><strong>In the same transaction as the adoption.</strong> A plain
 * {@code @EventListener} is synchronous and runs inside the publisher's
 * transaction, so the profile and its generations change owner together or not at
 * all. That is the property worth having: a person signs up to keep the CV they
 * just made, and a hand-over that carried the profile and lost the document would
 * have taken exactly the thing they came for.
 */
@Component
public class AdoptedGenerations {

    private static final Logger log = LoggerFactory.getLogger(AdoptedGenerations.class);

    private final AnonymousGenerations generations;

    AdoptedGenerations(AnonymousGenerations generations) {
        this.generations = generations;
    }

    @EventListener
    public void onProfileAdopted(AnonymousProfileAdopted adopted) {
        int carried = generations.adoptAll(adopted.profile(), adopted.owner());
        if (carried > 0) {
            // A count, and it is all there is to say (absolute rule 4).
            log.info("Carried {} anonymous generations into an account", carried);
        }
    }
}
