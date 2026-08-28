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
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobRetryPolicy;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.jobs.queue.ProgressSink;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
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
    private final EphemeralProfileWriter ephemeral;
    private final QuotaService quotas;
    private final JobQueue queue;
    private final Clock clock;

    ProfileExtractionJobHandler(ProfileStructuring structuring, ProfileNormalizer normalizer,
            ProfileWriter writer, EphemeralProfileWriter ephemeral, QuotaService quotas,
            JobQueue queue, Clock clock) {
        this.structuring = structuring;
        this.normalizer = normalizer;
        this.writer = writer;
        this.ephemeral = ephemeral;
        this.quotas = quotas;
        this.queue = queue;
        this.clock = clock;
    }

    @Override
    public JobType type() {
        return JobType.PROFILE_EXTRACT;
    }

    @Override
    public JobOutcome handle(Job job, ProgressSink progress) {
        UUID userId = job.getOwnerId();
        String anonSession = job.getAnonSessionId();
        if (userId == null && (anonSession == null || anonSession.isBlank())) {
            // A job belonging to nobody. JobOwner makes that unconstructable,
            // so this is a row written before it existed or by hand — and a
            // profile written for nobody would be readable by everybody.
            log.error("An ownerless extraction reached the queue; job {}", job.getId());
            return JobOutcome.failed(UserFacingError.of(ErrorCode.INTERNAL_ERROR), false);
        }
        var payload = ProfileExtractionPayload.from(job.getPayload());

        progress.report(READING);
        Result<ExtractedProfile> structured = structuring.structure(
                payload.asExtractedText(), bucketKeyFor(userId, anonSession), userId);

        return switch (structured) {
            case Result.Err<ExtractedProfile> failed -> {
                // Bolum 44.2: no profile came out of it, whatever the cause,
                // and it goes back to whoever paid — which for an anonymous
                // upload is an address the worker could not otherwise know.
                quotas.refund(payload.allowance(), QuotaMetric.PROFILE_EXTRACT);
                yield refused(failed.error());
            }
            case Result.Ok<ExtractedProfile> ok -> {
                progress.report(ORGANISING);
                NormalizedProfile normalized = normalizer.normalize(ok.value());
                progress.report(SAVING);
                yield userId == null
                        ? completedAnonymously(anonSession, normalized)
                        : completedForAccount(UserContext.of(userId), userId, normalized,
                                payload.replace());
            }
        };
    }

    /**
     * Which prompt variant this caller keeps seeing (Bolum 53.3).
     *
     * <p>An anonymous caller is bucketed by their profile id and not by the
     * session id it is derived from. Both are stable for the length of the
     * session, which is all bucketing asks for — and one of them is the
     * cookie, which has no business being passed around as an identifier.
     */
    private static String bucketKeyFor(UUID userId, String anonSession) {
        return userId != null
                ? userId.toString()
                : ProfileRef.ephemeral(AnonymousSessionId.of(anonSession)).id().toString();
    }

    /**
     * Bolum 9: an anonymous person's profile is written to Redis and to
     * nothing else.
     *
     * <p>No background work is queued for it. Embedding and measurement exist
     * to make the <em>first</em> generation good, and both write to rows this
     * profile does not have — an anonymous person gets an estimate and a
     * scoring pass without vectors, which Bolum 20.4 and Bolum 28.4 already
     * describe as the degraded-but-working path.
     */
    private JobOutcome completedAnonymously(String anonSession, NormalizedProfile normalized) {
        ProfileRef profile = ProfileRef.ephemeral(AnonymousSessionId.of(anonSession));
        ephemeral.write(profile, normalized);
        return completed(profile.id(), normalized);
    }

    private JobOutcome completedForAccount(UserContext user, UUID userId,
            NormalizedProfile normalized, boolean replace) {
        Profile profile = writer.write(user, normalized, replace);
        queueBackgroundWork(userId);
        return completed(profile.getId(), normalized);
    }

    /**
     * Bolum 31.6's background box, as two jobs rather than two waits.
     *
     * <p>The screen opens the moment the profile exists; the vectors and the
     * measured heights arrive underneath it while the person reads their own
     * CV. Done inline they would add twenty seconds to the one moment the
     * product asks anybody to wait, and neither is needed until the first
     * generation.
     *
     * <p><strong>Queued after the write and outside its transaction's
     * concern.</strong> A worker that picked one of these up before the rows
     * were committed would find an empty profile and do nothing — which is not
     * a failure it could report, only a profile that is quietly never
     * embedded. The write is committed by the time this handler returns
     * because {@code ProfileWriter} owns its own transaction and this method
     * has none.
     *
     * <p>Both are low priority (Bolum 30.3) and both are separately
     * retryable, because they fail for different reasons and neither failure
     * is the import's.
     */
    private void queueBackgroundWork(UUID userId) {
        queue.enqueue(new Job(JobType.EMBEDDING, userId, Map.of(), clock.instant()));
        queue.enqueue(new Job(JobType.MEASUREMENT, userId, Map.of(), clock.instant()));
    }

    /**
     * What the terminal SSE event of Bolum 30.6 carries.
     *
     * <p>Counts and ids, never content (absolute rule 4). The warning count is
     * here because Bolum 31.6's screen opens on the sections that have one —
     * the client needs to know whether to before it has fetched anything.
     */
    private static JobOutcome completed(UUID profileId, NormalizedProfile normalized) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profileId", profileId.toString());
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
