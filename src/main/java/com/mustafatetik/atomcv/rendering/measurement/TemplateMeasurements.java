package com.mustafatetik.atomcv.rendering.measurement;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Asks for a geometry to be measured, once (Bolum 33.3).
 *
 * <p>The second step of Bolum 33.3's flow, and it is triggered by a
 * generation rather than by the slider itself. Saving a preference nobody ever
 * renders would buy a compilation for a page that is never printed; asking
 * when a run actually falls back to {@link CapacityEstimator} measures exactly
 * what somebody is using. The person is not made to wait either way — that
 * run already produced a CV against the estimate, and the next one is exact.
 *
 * <p>The shape was decided by the architecture test rather than by taste: a
 * hook on the preference update made {@code profile} depend on
 * {@code rendering}, which already depends on {@code profile}, and the cycle
 * rule refused it. The better trigger was on the other side of the cycle.
 *
 * <p><strong>Nothing is queued for a geometry that is already known.</strong>
 * Both built-in templates at their own settings are measured constants, and
 * anything anybody has compiled before is a row — so the ordinary case, a
 * person who has never touched a slider, queues nothing at all.
 *
 * <p>The job carries no owner. A capacity belongs to a geometry, so the answer
 * is as useful to the next person who picks the same font size, and a job that
 * measured one is not one person's work.
 */
@Component
public class TemplateMeasurements {

    private static final Logger log = LoggerFactory.getLogger(TemplateMeasurements.class);

    private final JobQueue queue;
    private final Capacities capacities;
    private final Clock clock;

    TemplateMeasurements(JobQueue queue, Capacities capacities, Clock clock) {
        this.queue = queue;
        this.capacities = capacities;
        this.clock = clock;
    }

    /**
     * @return whether a job was queued. False is the ordinary answer and not a
     *         failure: it means the page at these settings is already known
     */
    public boolean request(TemplateCustomization customization) {
        if (capacities.isMeasured(customization)) {
            return false;
        }
        String costKey = customization.costKey();
        String key = "capacity:" + costKey;
        // enqueue() does not deduplicate; the caller does. Every other caller
        // has an owner to look a key up under and this one has none, so the
        // queue answers the ownerless form of the same question.
        if (queue.isPending(key)) {
            return false;
        }
        var job = new Job(JobType.MEASUREMENT, null,
                new TemplateMeasurementPayload(customization).toMap(), clock.instant());
        // Keyed by the geometry rather than by the moment, so two runs at one
        // setting compile it once.
        job.setIdempotencyKey(key);
        queue.enqueue(job);
        log.info("Queued a capacity measurement for {}", costKey);
        return true;
    }
}
