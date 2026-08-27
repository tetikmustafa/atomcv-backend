package com.mustafatetik.atomcv.rendering.measurement;

import com.mustafatetik.atomcv.compilation.CompilationException;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobHandler;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.jobs.queue.ProgressSink;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The measurement half of Bolum 31.6's background box.
 *
 * <p>What it buys is the guarantee in Bolum 20: a page limit that holds
 * because every wording's height was measured rather than estimated. Doing it
 * lazily at generation time would put fifteen seconds of XeLaTeX in front of
 * the person who asked for a CV; doing it here puts it behind the person
 * reading their own profile.
 *
 * <p><strong>One customization, not every one.</strong>
 * {@link TemplateCustomization#CLASSIC} is what a profile with untouched
 * preferences generates with, so it is the one measurement that will actually
 * be used. Measuring the rest eagerly would be XeLaTeX runs for pages nobody
 * has asked for, and the costs are filled in on demand when they are.
 *
 * <p><strong>A failure here is not a failed import.</strong> Selection falls
 * back to an estimate for anything unmeasured and says so (Bolum 20.4). What
 * is lost is the exactness, not the document.
 */
@Component
public class MeasurementJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(MeasurementJobHandler.class);

    private final RenderCostService costs;
    private final ProfileResolver profiles;

    MeasurementJobHandler(RenderCostService costs, ProfileResolver profiles) {
        this.costs = costs;
        this.profiles = profiles;
    }

    @Override
    public JobType type() {
        return JobType.MEASUREMENT;
    }

    @Override
    public JobOutcome handle(Job job, ProgressSink progress) {
        UUID userId = job.getOwnerId();
        if (userId == null) {
            log.error("An ownerless measurement job reached the queue; job {}", job.getId());
            return JobOutcome.failed(UserFacingError.of(ErrorCode.INTERNAL_ERROR), false);
        }
        try {
            int measured = costs.measureMissing(
                    profiles.resolve(UserContext.of(userId)), TemplateCustomization.CLASSIC);
            return JobOutcome.completed(Map.of("measured", measured));
        } catch (CompilationException unavailable) {
            // The compiler is a container, and a container that is not there
            // comes back. Retryable, and invisible until it is.
            log.warn("Measurement could not run; costs stay unmeasured: {}",
                    unavailable.getClass().getSimpleName());
            return JobOutcome.failed(UserFacingError.with(ErrorCode.COMPILATION_FAILED)
                    .param("detail", "measurement_unavailable")
                    .param("rawSourceAvailable", false)
                    .build(), true);
        }
    }
}
