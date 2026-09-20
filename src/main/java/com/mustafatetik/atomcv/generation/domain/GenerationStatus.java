package com.mustafatetik.atomcv.generation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * What became of a generation. Stored in {@code generations.status}.
 *
 * <p>There is no {@code queued} or {@code running} here, and that is the
 * design: a generation row is written when there is a document to describe.
 * While the work is in flight the thing to look at is the <em>job</em>, which
 * has its own four statuses and its own progress. Two state machines over one
 * piece of work would have to be kept in step, and nothing would report it
 * when they drifted.
 *
 * <p><strong>There is no {@code failed} either, and that took longer to
 * settle.</strong> The value was declared, published in the API schema, and
 * documented in its own javadoc as one nothing writes — which is a note saying
 * the defect out loud and leaving it there. {@code selection_state} is
 * {@code NOT NULL}, so a run that fails before selection has no row to write
 * and the failure lives on the job: the value was not waiting for a feature, it
 * was naming a state this schema cannot hold. V17 narrowed the column to match
 * (the sixth audit).
 */
public enum GenerationStatus {

    /** A document came out, and {@code selection_state} explains it. */
    COMPLETED,

    /** A later generation replaced this one — Faz G's edit loop. */
    SUPERSEDED;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static GenerationStatus fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<GenerationStatus> {
        public JpaConverter() {
            super(GenerationStatus.class);
        }
    }
}
