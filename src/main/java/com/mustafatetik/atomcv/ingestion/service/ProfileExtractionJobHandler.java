package com.mustafatetik.atomcv.ingestion.service;

import com.mustafatetik.atomcv.billing.QuotaMetric;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.ingestion.normalization.NormalizedProfile;
import com.mustafatetik.atomcv.ingestion.normalization.ProfileNormalizer;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile;
import com.mustafatetik.atomcv.ingestion.structuring.ProfileStructuring;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobHandler;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobProgress;
import com.mustafatetik.atomcv.jobs.queue.JobRetryPolicy;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.jobs.queue.ProgressSink;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The three stages of Bolum 31 behind one job.
 *
 * <p>Read (slice one) happened in the request, because Bolum 31.10's first
 * three failures — encrypted, scanned, empty — are things a person acts on at
 * once. What is left is the expensive half: structure the text, normalise what
 * came back, write it. Bolum 31.6 budgets around eight seconds for it and puts
 * a screen in front of the person while it runs, which is why it is a job with
 * progress rather than a long request.
 *
 * <p><strong>The quota is refunded on failure</strong> (Bolum 44.2). It was
 * taken when the upload was accepted, and a person who got no profile out of
 * it should not have paid for the attempt — the same rule the generation
 * handler follows, and for the same reason.
 */
@Component
public class ProfileExtractionJobHandler implements JobHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ProfileExtractionJobHandler.class);

    /** Bolum 30.6: a phase name a client can render, not a percentage invented per line. */
    private static final JobProgress READING =
            new JobProgress("structuring", "Reading your CV", 20);

    private static final JobProgress ORGANISING =
            new JobProgress("normalizing", "Organising what it found", 70);

    private static final JobProgress SAVING =
            new JobProgress("saving", "Saving your profile", 90);

    private final ProfileStructuring structuring;
    private final ProfileNormalizer normalizer;
    private final ProfileWriter writer;
    private final QuotaService quotas;

    ProfileExtractionJobHandler(ProfileStructuring structuring, ProfileNormalizer normalizer,
            ProfileWriter writer, QuotaService quotas) {
        this.structuring = structuring;
        this.normalizer = normalizer;
        this.writer = writer;
        this.quotas = quotas;
    }

    @Override
    public JobType type() {
        return JobType.PROFILE_EXTRACT;
    }

    @Override
    public JobOutcome handle(Job job, ProgressSink progress) {
        UUID userId = job.getOwnerId();
        if (userId == null) {
            // Bolum 9's anonymous flow is a later step and nothing enqueues
            // one yet. Refusing beats writing a profile that would belong to
            // nobody and be readable by everybody.
            log.error("An anonymous extraction reached the queue; job {}", job.getId());
            return JobOutcome.failed(UserFacingError.of(ErrorCode.INTERNAL_ERROR), false);
        }
        UserContext user = UserContext.of(userId);
        var payload = ProfileExtractionPayload.from(job.getPayload());

        progress.report(READING);
        Result<ExtractedProfile> structured =
                structuring.structure(payload.asExtractedText(), userId.toString());

        return switch (structured) {
            case Result.Err<ExtractedProfile> failed -> {
                // Bolum 44.2: no profile came out of it, whatever the cause.
                quotas.refund(user, QuotaMetric.PROFILE_EXTRACT);
                yield refused(failed.error());
            }
            case Result.Ok<ExtractedProfile> ok -> {
                progress.report(ORGANISING);
                NormalizedProfile normalized = normalizer.normalize(ok.value());
                progress.report(SAVING);
                yield completed(writer.write(user, normalized), normalized);
            }
        };
    }

    /**
     * What the terminal SSE event of Bolum 30.6 carries.
     *
     * <p>Counts and ids, never content (absolute rule 4). The warning count is
     * here because Bolum 31.6's screen opens on the sections that have one —
     * the client needs to know whether to before it has fetched anything.
     */
    private static JobOutcome completed(Profile profile, NormalizedProfile normalized) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profileId", profile.getId().toString());
        result.put("sectionCount", normalized.sections().size());
        result.put("atomCount", normalized.atoms().size());
        result.put("warningCount", normalized.warnings().size());
        result.put("detectedLanguage", normalized.language());
        return JobOutcome.completed(result);
    }

    /**
     * A refusal the user is told about, with the retryability the error itself
     * decides (Bolum 30.5).
     *
     * <p>Presented here rather than by {@code ErrorPresenter}: that class takes
     * a page height it would have nothing to do with, and the three failures
     * this handler can meet carry no resolutions to compute. What it must not
     * do is invent one — an outage says retry, and the other two say the CV is
     * the problem.
     */
    private static JobOutcome refused(PipelineError error) {
        UserFacingError presented = switch (error) {
            case PipelineError.LanguageUndetected torn -> UserFacingError
                    .with(ErrorCode.LANGUAGE_UNDETECTED)
                    .param("detectedCandidates", torn.candidates())
                    .build();
            case PipelineError.NothingExtracted ignored ->
                    UserFacingError.of(ErrorCode.EXTRACTION_EMPTY);
            case PipelineError.AllProvidersUnavailable outage -> UserFacingError
                    .with(ErrorCode.ALL_PROVIDERS_UNAVAILABLE)
                    .param("tried", outage.tried())
                    .build();
            default -> {
                // Nothing else can reach here: ProfileStructuring returns
                // three kinds and the chain returns the third. Said out loud
                // rather than mapped to a plausible code, because a wrong code
                // is a wrong sentence in front of a user.
                log.error("An unexpected pipeline error reached extraction: {}",
                        error.getClass().getSimpleName());
                yield UserFacingError.of(ErrorCode.INTERNAL_ERROR);
            }
        };
        return JobOutcome.failed(presented, JobRetryPolicy.isRetryable(error));
    }
}
