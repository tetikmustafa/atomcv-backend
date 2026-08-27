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
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.PipelineError;
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
    private final Clock clock;

    ProfileImportService(DocumentExtraction extraction, JobQueue queue, JobRepository jobs,
            QuotaService quotas, Clock clock) {
        this.extraction = extraction;
        this.queue = queue;
        this.jobs = jobs;
        this.quotas = quotas;
        this.clock = clock;
    }

    /**
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
    public Job importCv(UserContext user, String filename, String contentType,
            byte[] bytes, String idempotencyKey) {

        Optional<Job> already = jobs.findByIdempotencyKey(JobOwner.of(user), idempotencyKey);
        if (already.isPresent()) {
            return already.get();
        }

        Result<Void> spent = quotas.consume(QuotaSubject.of(user), QuotaMetric.PROFILE_EXTRACT);
        if (spent.isErr()) {
            throw spentAllowance(user, ((Result.Err<Void>) spent).error());
        }

        // Throws rather than returning: every refusal below is one the user
        // acts on, and none of them is a PipelineError. The unit is given back
        // first, because nothing was extracted and Bolum 44.2 refunds a
        // failure whatever caused it.
        ExtractedText document;
        try {
            document = extraction.extract(filename, contentType, bytes);
        } catch (RuntimeException refused) {
            quotas.refund(QuotaSubject.of(user), QuotaMetric.PROFILE_EXTRACT);
            throw refused;
        }

        var job = new Job(JobType.PROFILE_EXTRACT, user.userId(),
                ProfileExtractionPayload.of(document).asMap(), clock.instant());
        job.setIdempotencyKey(idempotencyKey);
        return queue.enqueue(job);
    }

    /**
     * Bolum 44.1 gives profile extraction its own, smaller counter, and the
     * code says which of the two ran out.
     *
     * <p>The limit is read back rather than passed in: the catalogue declares
     * it, a client renders it, and a number invented at the point of failure
     * would be the server misreporting its own configuration.
     */
    private ApiException spentAllowance(UserContext user, PipelineError error) {
        if (!(error instanceof PipelineError.QuotaExceeded spent)) {
            return ApiException.of(ErrorCode.INTERNAL_ERROR);
        }
        return new ApiException(UserFacingError.with(ErrorCode.PROFILE_QUOTA_EXCEEDED)
                .param("limit", quotas.usage(QuotaSubject.of(user), QuotaMetric.PROFILE_EXTRACT).limit())
                .param("resetsAt", spent.resetsAt())
                .build());
    }
}
