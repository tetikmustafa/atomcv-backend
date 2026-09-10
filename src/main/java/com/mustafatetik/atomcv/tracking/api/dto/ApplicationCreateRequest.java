package com.mustafatetik.atomcv.tracking.api.dto;

import com.mustafatetik.atomcv.tracking.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A job somebody applied to.
 *
 * @param generationId the CV sent with it, or null. It must be one of theirs;
 *                     somebody else's is a 400 rather than a 404, because the
 *                     field is wrong rather than the row missing
 * @param status       omitted means `applied`, which is what a person who has
 *                     just pressed the button means
 * @param appliedAt    omitted means today
 */
@Schema(name = "ApplicationCreate")
public record ApplicationCreateRequest(
        @NotBlank @Size(max = 200) String company,
        @NotBlank @Size(max = 200) String position,
        UUID generationId,
        ApplicationStatus status,
        LocalDate appliedAt,
        @Size(max = 5000) String notes) {
}
