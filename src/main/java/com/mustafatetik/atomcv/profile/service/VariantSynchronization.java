package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What happens to the other languages when one of them is edited
 * (Bolum 32.2).
 *
 * <p><strong>Everything derived goes stale; only some of it is regenerated.</strong>
 * The two are different questions and Bolum 32.2 answers them differently. A
 * wording whose source has moved on is out of step whoever wrote it, and the
 * person is entitled to know that — so the flag is set either way. But a
 * wording the person wrote themselves is theirs, and replacing it with a
 * machine translation because they fixed a typo in the Turkish would be the
 * product overruling them silently. That one is marked and left alone, and the
 * screen offers the choice.
 *
 * <p>Bolum 32.2 writes this as an event listener. It is a direct call instead:
 * there is one publisher and one subscriber, and an event between them would
 * buy indirection at the cost of the one thing that matters here — that the
 * marking happens inside the same transaction as the edit. A listener firing
 * after commit can be missed; a wording that stayed fresh after its source
 * changed is a wrong translation nobody is told about.
 */
@Service
public class VariantSynchronization {

    private static final Logger log = LoggerFactory.getLogger(VariantSynchronization.class);

    private final AtomVariantRepository variants;
    private final JobQueue queue;
    private final Clock clock;

    VariantSynchronization(AtomVariantRepository variants, JobQueue queue, Clock clock) {
        this.variants = variants;
        this.queue = queue;
        this.clock = clock;
    }

    /**
     * @param edited the wording whose words just changed
     * @return how many wordings were queued for regeneration
     */
    @Transactional
    public int afterEdit(ProfileRef profile, UserContext user, AtomVariant edited) {
        int queued = 0;
        for (AtomVariant derived : variants.derivedFrom(profile, edited.getId())) {
            derived.setStale(true);
            variants.save(profile, derived);
            if (!derived.isUserEdited()) {
                queue.enqueue(new Job(JobType.TRANSLATION, user.userId(),
                        Map.of(TranslationJobHandler.VARIANT_ID, derived.getId().toString()),
                        clock.instant()));
                queued++;
            }
        }
        if (queued > 0) {
            // Counts, never a sentence (absolute rule 4).
            log.info("An edit made {} wording(s) stale and queued them", queued);
        }
        return queued;
    }
}
