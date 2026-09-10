package com.mustafatetik.atomcv.tracking.api.dto;

import com.mustafatetik.atomcv.tracking.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A partial edit. Every field is optional and omitting one leaves it alone.
 *
 * <p>That is what makes this a PATCH: a screen moving one row from applied to
 * interview should not have to send the notes back with it and risk
 * overwriting an edit made in another tab.
 *
 * @param clearNotes the one thing null cannot say. Null notes means "leave
 *                   them", so emptying them needs a word of its own
 */
@Schema(name = "ApplicationUpdate", description = """
        A partial edit. Omitting a field leaves it as it is — send only what \
        changed.

        Requires `If-Match` with the version from the row you are editing. A \
        stale one is a 412: somebody else's tab changed the row first.""")
public record ApplicationUpdateRequest(
        @Size(max = 200) String company,
        @Size(max = 200) String position,
        UUID generationId,
        ApplicationStatus status,
        LocalDate appliedAt,
        @Size(max = 5000) String notes,
        boolean clearNotes) {
}
