package com.mustafatetik.atomcv.profile.service;

import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * Fields to change on an entry.
 *
 * <p>Null means "leave it alone" everywhere. For the nullable columns that is
 * not enough — clearing an end date is a real edit — so those carry a
 * JsonNullable: undefined leaves the field, a defined null clears it, and a
 * defined value sets it.
 */
public record EntryPatch(
        String title,
        JsonNullable<String> organization,
        JsonNullable<String> location,
        JsonNullable<LocalDate> startDate,
        JsonNullable<LocalDate> endDate,
        JsonNullable<String> url,
        Float importance,
        Boolean active,
        Boolean alwaysInclude,
        Boolean verbatim,
        Short minAtoms) {
}
