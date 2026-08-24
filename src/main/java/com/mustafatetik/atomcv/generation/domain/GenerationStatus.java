package com.mustafatetik.atomcv.generation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * What became of a generation. Stored in {@code generations.status}
 * (Bolum 13).
 *
 * <p>There is no {@code queued} or {@code running} here, and that is the
 * design: a generation row is written when there is a document to describe.
 * While the work is in flight the thing to look at is the <em>job</em>, which
 * has its own five statuses and its own progress. Two state machines over one
 * piece of work would have to be kept in step, and nothing would report it
 * when they drifted.
 */
public enum GenerationStatus {

    /** A document came out, and {@code selection_state} explains it. */
    COMPLETED,

    /**
     * Reserved. Nothing writes it today: {@code selection_state} is
     * {@code NOT NULL}, so a run that failed before selection has no row to
     * write and the failure lives on the job instead.
     */
    FAILED,

    /** A later generation replaced this one — Faz G's edit loop (Bolum 24). */
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
