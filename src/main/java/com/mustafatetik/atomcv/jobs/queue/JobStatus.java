package com.mustafatetik.atomcv.jobs.queue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Where a job is. Stored in {@code jobs.status}, which carries a {@code CHECK}
 * over exactly these five values (Bolum 13).
 *
 * <p>{@link #COMPLETED}, {@link #FAILED} and {@link #CANCELLED} are terminal.
 * The progress stream ends on one of them, and a stream that closes without
 * one leaves the user's screen spinning — which is why the status endpoint
 * exists as a fallback (EK D.6.4).
 */
public enum JobStatus {

    QUEUED,

    /** Claimed by a worker, which is proving it is alive by its heartbeat. */
    RUNNING,

    COMPLETED,

    /** Out of attempts, or the error was never worth retrying (Bolum 30.5). */
    FAILED,

    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
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
