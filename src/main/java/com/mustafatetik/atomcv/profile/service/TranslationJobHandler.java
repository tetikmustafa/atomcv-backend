package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobHandler;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobRetryPolicy;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.jobs.queue.ProgressSink;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bolum 32.2's background translation.
 *
 * <p><strong>Three ways to have nothing to do, and none of them is a
 * failure.</strong> The wording may have been deleted since the job was
 * queued; the person may have edited it in the meantime, which Bolum 32.2
 * protects absolutely; or its source may have been emptied. A queue is not the
 * only thing that touches a profile, and a job that reports a failure because
 * the world moved on would put a red mark on a screen for something that is
 * simply finished.
 *
 * <p>What the person sees either way is the staleness flag. A wording that
 * could not be regenerated stays marked, which is the true statement about it,
 * and the screen keeps offering the choice Bolum 32.2 describes.
 */
@Component
public class TranslationJobHandler implements JobHandler {

    /** The wording to bring back into step. */
    static final String VARIANT_ID = "variantId";

    private static final Logger log = LoggerFactory.getLogger(TranslationJobHandler.class);

    private final VariantTranslationService translations;
    private final AtomVariantRepository variants;
    private final AtomRepository atoms;
    private final ProfileResolver profiles;

    TranslationJobHandler(VariantTranslationService translations,
            AtomVariantRepository variants, AtomRepository atoms, ProfileResolver profiles) {
        this.translations = translations;
        this.variants = variants;
        this.atoms = atoms;
        this.profiles = profiles;
    }

    @Override
    public JobType type() {
        return JobType.TRANSLATION;
    }

    @Override
    public JobOutcome handle(Job job, ProgressSink progress) {
        UUID userId = job.getOwnerId();
        if (userId == null) {
            log.error("An ownerless translation reached the queue; job {}", job.getId());
            return JobOutcome.failed(UserFacingError.of(ErrorCode.INTERNAL_ERROR), false);
        }
        UUID variantId = variantIdOf(job);
        if (variantId == null) {
            log.error("A translation carried no wording to translate; job {}", job.getId());
            return JobOutcome.failed(UserFacingError.of(ErrorCode.INTERNAL_ERROR), false);
        }

        ProfileRef profile = profiles.resolve(UserContext.of(userId));
        Optional<AtomVariant> target = variants.findById(profile, variantId);
        if (target.isEmpty()) {
            return nothingToDo("the wording was deleted");
        }
        if (target.get().isUserEdited()) {
            // Bolum 32.2, and the only one of the three that is a decision
            // rather than an accident: an edit landed after this was queued,
            // and the person's own words win.
            return nothingToDo("the person wrote it themselves");
        }

        Optional<AtomVariant> source = Optional.ofNullable(
                        target.get().getDerivedFromVariantId())
                .flatMap(id -> variants.findById(profile, id));
        Optional<Atom> atom = atoms.findById(profile, target.get().getAtomId());
        if (source.isEmpty() || atom.isEmpty() || source.get().getContent().isEmpty()) {
            return nothingToDo("there is nothing to translate from");
        }

        Result<AtomVariant> retranslated = translations.retranslate(
                profile, variantId, source.get(), atom.get(), userId.toString());

        return switch (retranslated) {
            case Result.Ok<AtomVariant> ok -> JobOutcome.completed(Map.of(
                    "translated", true, "language", ok.value().getLanguage()));
            case Result.Err<AtomVariant> failed -> refused(failed.error());
        };
    }

    /**
     * Completed, and it says why nothing happened.
     *
     * <p>{@code jobs.result} is read by a client and by whoever is debugging
     * later, and "translated: false" with no reason is the shape that costs an
     * afternoon. The reason is a fixed phrase, never anything the user wrote.
     */
    private static JobOutcome nothingToDo(String why) {
        return JobOutcome.completed(Map.of("translated", false, "reason", why));
    }

    private static JobOutcome refused(PipelineError error) {
        UserFacingError presented = switch (error) {
            case PipelineError.TranslationRejected ignored ->
                    UserFacingError.of(ErrorCode.TRANSLATION_FAILED);
            case PipelineError.AllProvidersUnavailable outage -> UserFacingError
                    .with(ErrorCode.ALL_PROVIDERS_UNAVAILABLE)
                    .param("tried", outage.tried())
                    .build();
            default -> {
                log.error("An unexpected pipeline error reached translation: {}",
                        error.getClass().getSimpleName());
                yield UserFacingError.of(ErrorCode.INTERNAL_ERROR);
            }
        };
        return JobOutcome.failed(presented, JobRetryPolicy.isRetryable(error));
    }

    private static UUID variantIdOf(Job job) {
        Object value = job.getPayload().get(VARIANT_ID);
        try {
            return value == null ? null : UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }
}
