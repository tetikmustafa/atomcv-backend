package com.mustafatetik.atomcv.ingestion.service;

import com.mustafatetik.atomcv.billing.QuotaMetric;
import com.mustafatetik.atomcv.billing.QuotaSubject;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.ingestion.extraction.DocumentExtraction;
import com.mustafatetik.atomcv.ingestion.extraction.ExtractedText;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobOwner;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobRepository;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Resolution;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * An uploaded CV, read here and finished later (Bolum 31.1, Bolum 31.2).
 *
 * <p><strong>The reading is synchronous and the rest is not</strong>, and the
 * split is Bolum 31.10's first three rows. An encrypted PDF, a scanned one and
 * a file with nothing in it are all things a person acts on immediately — a
 * different file, or the manual form. Discovering any of them from a job that
 * failed eight seconds later would be the same information, delivered where it
 * is least useful. What is left after the read is the expensive half, and that
 * is what the queue is for.
 *
 * <p>The order of the gates is the same as everywhere else in this system:
 * idempotency, then the quota, then the work. The quota after idempotency
 * because a repeated request must not spend a second unit; the extraction
 * after the quota because it is the only step here that costs anything.
 */
@Service
public class ProfileImportService {

    private final DocumentExtraction extraction;
    private final JobQueue queue;
    private final JobRepository jobs;
    private final QuotaService quotas;
    private final ProfileResolver profiles;
    private final Clock clock;

    ProfileImportService(DocumentExtraction extraction, JobQueue queue, JobRepository jobs,
            QuotaService quotas, ProfileResolver profiles, Clock clock) {
        this.extraction = extraction;
        this.queue = queue;
        this.jobs = jobs;
        this.quotas = quotas;
        this.profiles = profiles;
        this.clock = clock;
    }

    /**
     * @param owner          who is asking — an account or an anonymous session
     *                       (Adim 3.6). Both may upload a CV; where the result
     *                       is kept is the only difference, and the job carries
     *                       the answer.
     * @param allowance      whose daily ceiling this spends. An account's is
     *                       its own; an anonymous caller's is their address,
     *                       because a session is a cookie and a cookie is
     *                       something anybody can throw away and ask for
     *                       another (Bolum 44.1).
     * @param idempotencyKey the request header, or null (Bolum 30.7). An upload
     *                       is the one request a flaky connection makes twice
     *                       most easily, and the second one would spend a
     *                       second unit of the smallest allowance in the
     *                       product.
     * @return the queued job
     * @throws ApiException for every refusal — the allowance is spent, or the
     *         extraction ladder turned the file away. Presented here rather
     *         than returned as a value because the controller has nothing to
     *         add: {@code ErrorPresenter} takes a page height that means
     *         nothing to an upload, and a second presenter at the edge would
     *         be a second place for the catalogue to drift from.
     */
    public Job importCv(JobOwner owner, QuotaSubject allowance, String filename,
            String contentType, byte[] bytes, String idempotencyKey, boolean replace) {

        Optional<Job> already = jobs.findByIdempotencyKey(owner, idempotencyKey);
        if (already.isPresent()) {
            return already.get();
        }

        refuseASecondProfile(owner, replace);

        Result<Void> spent = quotas.consume(allowance, QuotaMetric.PROFILE_EXTRACT);
        if (spent.isErr()) {
            throw spentAllowance(allowance, ((Result.Err<Void>) spent).error());
        }

        // Throws rather than returning: every refusal below is one the user
        // acts on, and none of them is a PipelineError. The unit is given back
        // first, because nothing was extracted and Bolum 44.2 refunds a
        // failure whatever caused it.
        ExtractedText document;
        try {
            document = extraction.extract(filename, contentType, bytes);
        } catch (RuntimeException refused) {
            quotas.refund(allowance, QuotaMetric.PROFILE_EXTRACT);
            throw refused;
        }

        var job = new Job(JobType.PROFILE_EXTRACT, owner.userId(),
                ProfileExtractionPayload.of(document, allowance, replace).asMap(),
                clock.instant());
        job.setAnonSessionId(owner.anonSessionId());
        job.setIdempotencyKey(idempotencyKey);
        return queue.enqueue(job);
    }

    /**
     * The sixth synchronous refusal (Bolum 31.6.2), and the one that had been
     * missing.
     *
     * <p>{@code PROFILE_ALREADY_EXISTS} was in the catalogue and nothing
     * produced it: a second CV was <em>added</em> to the profile that was
     * already there, so the sections arrived twice and the person found out in
     * the editor. Refusing here rather than in the worker matters — a 202
     * followed by a failed job is a worse answer than a 409, and the caller
     * still has the file in hand.
     *
     * <p>Two resolutions and no third, as Bolum 08b's table says: replace or
     * keep. A merge would be atom-level de-duplication and is Stage 4 work;
     * offering it now would either name an action the server cannot perform or
     * ship the silent duplication this refusal exists to stop.
     *
     * <p><strong>Accounts only.</strong> An anonymous upload writes the whole
     * ephemeral document at once (§ 31.6.3), so a second one replaces rather
     * than doubles and there is nothing to warn about.
     */
    private void refuseASecondProfile(JobOwner owner, boolean replace) {
        if (replace || owner.isAnonymous()) {
            return;
        }
        if (profiles.hasContent(UserContext.of(owner.userId()))) {
            throw ApiException.of(ErrorCode.PROFILE_ALREADY_EXISTS,
                    Resolution.of(ResolutionAction.REPLACE_PROFILE),
                    Resolution.of(ResolutionAction.KEEP_EXISTING_PROFILE));
        }
    }

    /**
     * Bolum 44.1 gives profile extraction its own, smaller counter, and the
     * code says which of the two ran out.
     *
     * <p>The limit is read back rather than passed in: the catalogue declares
     * it, a client renders it, and a number invented at the point of failure
     * would be the server misreporting its own configuration.
     */
    private ApiException spentAllowance(QuotaSubject allowance, PipelineError error) {
        if (!(error instanceof PipelineError.QuotaExceeded spent)) {
            return ApiException.of(ErrorCode.INTERNAL_ERROR);
        }
        return new ApiException(UserFacingError.with(ErrorCode.PROFILE_QUOTA_EXCEEDED)
                .param("limit", quotas.usage(allowance, QuotaMetric.PROFILE_EXTRACT).limit())
                .param("resetsAt", spent.resetsAt())
                .build());
    }
}
