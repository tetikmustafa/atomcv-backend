package com.mustafatetik.atomcv.tracking.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mustafatetik.atomcv.shared.util.LowercaseEnumConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Where an application got to (Bolum 13's `applications.status`).
 *
 * <p><strong>A closed set, and the column has enforced it since V1.</strong>
 * The same reasoning as every other closed vocabulary here: the client writes
 * a sentence per value, so a value the server can emit and the client has
 * never seen renders as a raw key to a person.
 *
 * <p>These five are a funnel and not a state machine. Nothing here forbids
 * going from `rejected` back to `interview` -- a company that reopens a
 * process is not a data error, and a tracker that argued with its user about
 * what happened to them would be worse than one that believed them.
 */
public enum ApplicationStatus {

    /** Sent, and nothing has come back yet. */
    APPLIED,

    /** Somebody replied and there is a conversation. */
    INTERVIEW,

    OFFER,

    /** They said no. */
    REJECTED,

    /** The person said no, or stopped caring. Their own record, their own word. */
    WITHDRAWN;

    /** Lowercase on the wire and in the column, as V1's CHECK constraint spells it. */
    @JsonValue
    public String wireValue() {
        // Locale.ROOT: absolute rule 7.
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ApplicationStatus fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowercaseEnumConverter<ApplicationStatus> {
        public JpaConverter() {
            super(ApplicationStatus.class);
        }
    }
}
