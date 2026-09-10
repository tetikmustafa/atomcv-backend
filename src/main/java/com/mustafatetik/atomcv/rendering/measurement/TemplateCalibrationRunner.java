package com.mustafatetik.atomcv.rendering.measurement;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.rendering.repository.MeasuredCapacities;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns an estimate into a measurement (Bolum 33.3).
 *
 * <p>The third step of Bolum 33.3's flow. A slider moves, this is queued, and
 * until it lands a generation runs against {@link CapacityEstimator}'s scaled
 * guess with a margin. Afterwards the same geometry is exact and spends the
 * whole page.
 *
 * <p>It runs under {@code MEASUREMENT}, the lowest priority in the queue, and
 * that is right: nobody is watching it. A person who asked for a CV in the
 * meantime already has one.
 *
 * <p><strong>Not a {@code JobHandler} of its own.</strong> That type is taken
 * by the handler that measures a profile's atom costs, and a second one would
 * have stopped the worker from starting — which is how this was found. The two
 * are told apart by the thing that already distinguished them: a profile's
 * measurement belongs to somebody and a template's belongs to nobody.
 *
 * <p><strong>Nothing about a person is in it.</strong> The payload is a
 * geometry, the answer is filed under that geometry, and the row it writes is
 * as useful to the next person who picks the same font size. A job that failed
 * costs nobody a CV — it costs everybody a slightly smaller page until it is
 * tried again.
 */
@Component
public class TemplateCalibrationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(TemplateCalibrationRunner.class);

    private final CalibrationService calibration;
    private final MeasuredCapacities capacities;

    TemplateCalibrationRunner(
            CalibrationService calibration, MeasuredCapacities capacities) {

        this.calibration = calibration;
        this.capacities = capacities;
    }

    /** @param job an ownerless measurement job, which is what a calibration is */
    public JobOutcome run(Job job) {
        TemplateCustomization customization =
                TemplateMeasurementPayload.from(job.getPayload()).customization();
        String costKey = customization.costKey();

        // Somebody else may have measured it between the enqueue and now: two
        // people moving the same slider queue two jobs, and the second has
        // nothing to do. Cheaper to look than to compile.
        if (capacities.find(costKey).isPresent()) {
            log.info("Capacity for {} was already measured; nothing to do", costKey);
            return JobOutcome.completed(result(costKey, false));
        }

        return calibration.measure(customization)
                .map(capacity -> {
                    capacities.store(costKey, capacity);
                    log.info("Measured a capacity for {}", costKey);
                    return JobOutcome.completed(result(costKey, true));
                })
                // A calibration that did not compile is a defect in a preamble
                // or a container, not something the person who moved the
                // slider did. Retried, because the world outside changes --
                // and their CVs keep coming out on the estimate meanwhile,
                // which is why this failing quietly is survivable.
                .orElseGet(() -> JobOutcome.failed(
                        UserFacingError.of(ErrorCode.COMPILATION_FAILED), true));
    }

    private static Map<String, Object> result(String costKey, boolean measured) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("costKey", costKey);
        result.put("measured", measured);
        return result;
    }
}
