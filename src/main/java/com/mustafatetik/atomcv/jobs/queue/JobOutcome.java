package com.mustafatetik.atomcv.jobs.queue;

import com.mustafatetik.atomcv.shared.error.UserFacingError;
import java.util.Map;
import java.util.Objects;

/**
 * What a handler did with a job.
 *
 * <p>Presentation happens in the handler and not here, which is what keeps the
 * queue independent of the work: {@code jobs} would otherwise have to know
 * about {@code PipelineError}, {@code ErrorPresenter} and the page height a
 * conflict is measured against, and it would know them for every future job
 * type as well.
 *
 * <p>Retryability comes back with the failure for the same reason. Bolum 30.5
 * decides it from the error, and only the handler holds the error in the form
 * that decision is made from ({@link JobRetryPolicy} is what it asks).
 */
public sealed interface JobOutcome {

    /** @param result what {@code jobs.result} stores; never user content */
    record Completed(Map<String, Object> result) implements JobOutcome {

        public Completed {
            result = result == null ? Map.of() : Map.copyOf(result);
        }
    }

    /**
     * @param error     the catalogue code and its params, which is what the
     *                  user is eventually shown
     * @param retryable whether another attempt could plausibly succeed. A
     *                  false here is final however many attempts are left:
     *                  retrying a profile that is too thin just fails three
     *                  times instead of once.
     */
    record Failed(UserFacingError error, boolean retryable) implements JobOutcome {

        public Failed {
            Objects.requireNonNull(error, "error");
        }
    }

    static JobOutcome completed(Map<String, Object> result) {
        return new Completed(result);
    }

    static JobOutcome failed(UserFacingError error, boolean retryable) {
        return new Failed(error, retryable);
    }
}
