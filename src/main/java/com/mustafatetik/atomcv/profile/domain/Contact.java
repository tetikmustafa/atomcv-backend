package com.mustafatetik.atomcv.profile.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The contact block of a profile, stored in {@code profiles.contact}
 * (Bolum 14.2).
 *
 * <p>A typed record rather than a map: every field here is rendered into a CV
 * header, and a map would push "which keys exist" into the renderer and the
 * frontend at once.
 *
 * <p>All of it is personal data. {@code toString} says nothing (absolute
 * rule 4) — this record is the single most likely thing to end up in a log line
 * by accident.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Contact(
        String name,
        String email,
        String phone,
        String linkedin,
        String github,
        String website,
        String location) {

    public static final Contact EMPTY = new Contact(null, null, null, null, null, null, null);

    /**
     * Not a JSON property. Jackson would otherwise write {@code "empty": false}
     * into the column and then refuse to read the row back, because the record
     * has no such component.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return name == null && email == null && phone == null
                && linkedin == null && github == null && website == null && location == null;
    }

    @Override
    public String toString() {
        return "Contact[" + (isEmpty() ? "empty" : "filled") + "]";
    }
}
