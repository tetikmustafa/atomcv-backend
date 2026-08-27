package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.embedding.EmbeddingException;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobHandler;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.jobs.queue.ProgressSink;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The embedding half of Bolum 31.6's background box.
 *
 * <p>Queued when an import finishes and never waited on. The review screen
 * opens as soon as the profile exists; this runs underneath it, and by the
 * time the person has finished reading their own CV the vectors are there.
 * Doing it inside the extraction would have added five seconds to the one
 * moment the product asks somebody to wait.
 *
 * <p><strong>A failure here is not a failed import.</strong> Bolum 28.4 already
 * has scoring fall back to running without the embedding component — quality
 * drops, the product keeps working — so an embedding service that is down
 * costs a slightly worse first generation and nothing else. It is retryable
 * for the same reason it is not fatal: the world outside changes.
 */
@Component
public class ProfileMaintenanceJobHandler implements JobHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ProfileMaintenanceJobHandler.class);

    private final AtomEmbeddingService embeddings;
    private final ProfileResolver profiles;

    ProfileMaintenanceJobHandler(AtomEmbeddingService embeddings, ProfileResolver profiles) {
        this.embeddings = embeddings;
        this.profiles = profiles;
    }

    @Override
    public JobType type() {
        return JobType.EMBEDDING;
    }

    @Override
    public JobOutcome handle(Job job, ProgressSink progress) {
        UUID userId = job.getOwnerId();
        if (userId == null) {
            log.error("An ownerless embedding job reached the queue; job {}", job.getId());
            return JobOutcome.failed(UserFacingError.of(ErrorCode.INTERNAL_ERROR), false);
        }
        try {
            int embedded = embeddings.embedMissing(
                    profiles.resolve(UserContext.of(userId)));
            return JobOutcome.completed(Map.of("embedded", embedded));
        } catch (EmbeddingException unavailable) {
            // Bolum 28.4: worth another attempt, and worth nobody's attention
            // in the meantime.
            log.warn("Embedding is not answering; the profile stays unembedded: {}",
                    unavailable.getClass().getSimpleName());
            return JobOutcome.failed(UserFacingError.of(ErrorCode.EMBEDDING_UNAVAILABLE), true);
        }
    }
}
