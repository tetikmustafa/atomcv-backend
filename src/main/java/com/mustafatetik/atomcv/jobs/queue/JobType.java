package com.mustafatetik.atomcv.jobs.queue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * What a job is, and how urgently it is taken.
 *
 * <p>The priority travels with the type rather than with the caller, because
 * "how long may this wait" is a property of the work and not of whoever
 * enqueued it. A lower number is taken first, which is the order the claim
 * query sorts by.
 *
 * <p>The whole vocabulary of {@code jobs.type} is here, and since V17 the
 * column carries a constraint saying so — it had a comment naming six and a
 * constraint naming none.
 *
 * <p><strong>{@code EMAIL} was the sixth and is gone.</strong> It sat here with
 * a priority and a sentence about late magic links, and nothing enqueued one:
 * mail goes out on a post-commit event, which the fifth audit moved it to. The
 * paragraph above used to argue that naming a type nothing enqueues costs
 * nothing, and it was half right — it costs nothing to the queue, and it tells
 * the next reader that mail is queued work when it is not. A type comes back
 * the day something enqueues it (the sixth audit).
 */
public enum JobType {

    /** The user is on the screen waiting for it. */
    GENERATION(10),

    /** Likewise, and it is the first thing they ever do. */
    PROFILE_EXTRACT(50),

    TRANSLATION(100),

    EMBEDDING(150),

    MEASUREMENT(200);

    private final short priority;

    JobType(int priority) {
        this.priority = (short) priority;
    }

    /** Lower is taken first. */
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
