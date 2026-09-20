package com.mustafatetik.atomcv.jobs.queue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Where a job is. Stored in {@code jobs.status}, which carries a {@code CHECK}
 * over exactly these four values.
 *
 * <p>{@link #COMPLETED} and {@link #FAILED} are terminal. The progress stream
 * ends on one of them, and a stream that closes without one leaves the user's
 * screen spinning — which is why the status endpoint exists as a fallback.
 *
 * <p><strong>There was a fifth, {@code CANCELLED}, and nothing could reach
 * it.</strong> {@code Job.cancel} had one caller and it was a test of itself;
 * no endpoint cancels a job and the resource map names none. It was published
 * in {@code JobStatusResponse} all the same, so a client could write the branch
 * and wait forever to enter it. V17 took it out of the column. Cancelling is a
 * feature, and the day it is one the value comes back with it (the sixth
 * audit).
 */
public enum JobStatus {

    QUEUED,

    /** Claimed by a worker, which is proving it is alive by its heartbeat. */
    RUNNING,

    COMPLETED,

    /** Out of attempts, or the error was never worth retrying. */
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static JobStatus fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<JobStatus> {
        public JpaConverter() {
            super(JobStatus.class);
        }
    }
}
