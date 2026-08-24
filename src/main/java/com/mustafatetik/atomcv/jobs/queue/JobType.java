package com.mustafatetik.atomcv.jobs.queue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * What a job is, and how urgently it is taken (Bolum 30.3).
 *
 * <p>The priority travels with the type rather than with the caller, because
 * "how long may this wait" is a property of the work and not of whoever
 * enqueued it. A lower number is taken first, which is the order the claim
 * query sorts by.
 *
 * <p>The whole vocabulary of {@code jobs.type} is here, including the kinds
 * nothing enqueues yet. A queue that could not name a type would take a row it
 * has no handler for and fail it as unknown; naming them costs nothing and the
 * column already lists all six (Bolum 13).
 */
public enum JobType {

    /** The user is on the screen waiting for it. */
    GENERATION(10),

    /** Likewise, and it is the first thing they ever do. */
    PROFILE_EXTRACT(50),

    /** A magic link that arrives late is a login that failed. */
    EMAIL(80),

    TRANSLATION(100),

    EMBEDDING(150),

    MEASUREMENT(200);

    private final short priority;

    JobType(int priority) {
        this.priority = (short) priority;
    }

    /** Bolum 30.3. Lower is taken first. */
    public short priority() {
        return priority;
    }

    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static JobType fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<JobType> {
        public JpaConverter() {
            super(JobType.class);
        }
    }
}
