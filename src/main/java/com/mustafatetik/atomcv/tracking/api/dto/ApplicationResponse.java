package com.mustafatetik.atomcv.tracking.api.dto;

import com.mustafatetik.atomcv.tracking.domain.Application;
import com.mustafatetik.atomcv.tracking.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One tracked application (Bolum 55).
 *
 * @param generationId the CV it was sent with, or null once that CV has been
 *                     deleted. Null is not "never had one" and the screen
 *                     should not offer a download for it
 * @param version      what an {@code If-Match} has to carry to edit this row.
 *                     Also on the ETag header of a single read
 */
@Schema(name = "Application")
public record ApplicationResponse(
        UUID id,
        UUID generationId,
        String company,
        String position,
        ApplicationStatus status,
        LocalDate appliedAt,
        String notes,
        Instant createdAt,
        long version) {

    public static ApplicationResponse of(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getGenerationId(),
                application.getCompany(),
                application.getPosition(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getNotes(),
                application.getCreatedAt(),
                application.getVersion() == null ? 0L : application.getVersion());
    }
}
