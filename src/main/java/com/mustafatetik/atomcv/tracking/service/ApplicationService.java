package com.mustafatetik.atomcv.tracking.service;

import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.util.EntityTags;
import com.mustafatetik.atomcv.tracking.domain.Application;
import com.mustafatetik.atomcv.tracking.domain.ApplicationStatus;
import com.mustafatetik.atomcv.tracking.repository.ApplicationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where somebody applied, and what happened (Bolum 55).
 *
 * <p>A small resource, and deliberately so. It records what a person tells it
 * and argues with them about almost nothing: a company that reopens a closed
 * process is not a data error, so no transition here is forbidden. What it
 * does check is ownership, and it checks it the same way everything else does.
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applications;
    private final GenerationRepository generations;

    ApplicationService(ApplicationRepository applications, GenerationRepository generations) {
        this.applications = applications;
        this.generations = generations;
    }

    @Transactional(readOnly = true)
    public List<Application> list(UserContext user) {
        return applications.findAll(user);
    }

    /**
     * @param generationId the CV this was sent with, or null. Checked through
     *                     the scoped repository rather than trusted: an id a
     *                     browser sends is an id somebody can change, and
     *                     linking a stranger's generation would put its
     *                     download one hop away from a row this person owns
     */
    @Transactional
    public Application create(UserContext user, String company, String position,
            UUID generationId, ApplicationStatus status, LocalDate appliedAt, String notes) {

        var application = new Application(user.userId(), company, position);
        application.setGenerationId(ownedGeneration(user, generationId));
        if (status != null) {
            application.setStatus(status);
        }
        if (appliedAt != null) {
            application.setAppliedAt(appliedAt);
        }
        application.setNotes(notes);
        return applications.save(user, application);
    }

    /**
     * A partial update, guarded by {@code If-Match} (Bolum 35.6).
     *
     * <p>Null means "leave it", which is what makes this a PATCH rather than a
     * replacement: a screen that let somebody move one row from applied to
     * interview should not have to send the notes back with it and risk
     * overwriting an edit made in another tab.
     *
     * <p>The one exception is {@code notes}, where clearing has to be
     * expressible — {@code clearNotes} says so, because null cannot mean both
     * "leave it" and "empty it".
     */
    @Transactional
    public Application update(UserContext user, UUID id, String ifMatch,
            String company, String position, UUID generationId, ApplicationStatus status,
            LocalDate appliedAt, String notes, boolean clearNotes) {

        Application application = applications.findById(user, id)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));
        EntityTags.requireMatch(ifMatch, application.getVersion());

        if (company != null) {
            application.setCompany(company);
        }
        if (position != null) {
            application.setPosition(position);
        }
        if (status != null) {
            application.setStatus(status);
        }
        if (appliedAt != null) {
            application.setAppliedAt(appliedAt);
        }
        if (generationId != null) {
            application.setGenerationId(ownedGeneration(user, generationId));
        }
        if (clearNotes) {
            application.setNotes(null);
        } else if (notes != null) {
            application.setNotes(notes);
        }
        return applications.save(user, application);
    }

    @Transactional
    public void delete(UserContext user, UUID id, String ifMatch) {
        Application application = applications.findById(user, id)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));
        // Guarded like an edit, and for the same reason: a row deleted from a
        // stale screen is a row somebody else's tab had just changed.
        EntityTags.requireMatch(ifMatch, application.getVersion());
        applications.delete(user, application);
    }

    /**
     * The generation, if this person owns it.
     *
     * <p>Absolute rule 3. A generation id reaches a browser in the job's
     * terminal event and in the download link, so it is an id somebody can
     * change to somebody else's — and a row linking one would put a stranger's
     * CV one hop from a resource this person owns.
     */
    private UUID ownedGeneration(UserContext user, UUID generationId) {
        if (generationId == null) {
            return null;
        }
        if (!generations.exists(user, generationId)) {
            throw new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                    .param("fields", List.of("generationId"))
                    .build());
        }
        return generationId;
    }
}
