package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.billing.QuotaMetric;
import com.mustafatetik.atomcv.billing.QuotaSubject;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.generation.domain.EngineVersion;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.RenderedContent;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.phases.analysis.JobDescriptionDigest;
import com.mustafatetik.atomcv.generation.pipeline.ErrorPresenter;
import com.mustafatetik.atomcv.generation.pipeline.GeneratedDocument;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobHandler;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobRetryPolicy;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.jobs.queue.ProgressSink;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
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
 * The queued half of a job-specific generation (Bolum 30, Bolum 35.3).
 *
 * <p>Where the two modules meet, and the meeting is one-directional:
 * {@code jobs} knows how to run a handler and nothing about generations, this
 * knows how to make a CV and treats the queue as a caller. Everything the
 * queue would otherwise have to understand — how a {@link PipelineError} is
 * presented, whether it is worth retrying, what a generation record looks like
 * — is decided here.
 *
 * <p>A row is written only when a document came out. {@code selection_state}
 * is what the row is <em>for</em>, and a run that failed before selection has
 * none; that failure lives on the job, which is what the user's screen is
 * watching anyway.
 */
@Component
public class GenerationJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobHandler.class);

    private final JobSpecificGenerationService generations;
    private final CvGenerationService general;
    private final GenerationRepository records;
    private final QuotaService quotas;
    private final ErrorPresenter errors;

    GenerationJobHandler(JobSpecificGenerationService generations, CvGenerationService general,
            GenerationRepository records, QuotaService quotas, ErrorPresenter errors) {

        this.quotas = quotas;
        this.generations = generations;
        this.general = general;
        this.records = records;
        this.errors = errors;
    }

    private static boolean isGeneralMode(GenerationPayload payload) {
        return payload.jobDescription() == null || payload.jobDescription().isBlank();
    }

    @Override
    public JobType type() {
        return JobType.GENERATION;
    }

    @Override
    public JobOutcome handle(Job job, ProgressSink progress) {
        UUID userId = job.getOwnerId();
        if (userId == null) {
            // Bolum 9's anonymous flow is Stage 3 and nothing enqueues one
            // yet. Refusing beats inventing an owner for a row that would then
            // belong to nobody and be readable by everybody.
            log.error("An anonymous generation reached the queue; job {}", job.getId());
            return JobOutcome.failed(UserFacingError.of(ErrorCode.INTERNAL_ERROR), false);
        }

        GenerationPayload payload = GenerationPayload.from(job.getPayload());
        UserContext user = UserContext.of(userId);

        // Bolum 19.4: no posting means no Faz A and no Faz B. Everything from
        // selection onwards is the same code, which is what separating scoring
        // from selection bought.
        Result<GeneratedGeneration> result = isGeneralMode(payload)
                ? general.generateGeneralCv(user, payload.maxPages(), payload.language(),
                        progress)
                : generations.generateForJob(
                        user, payload.jobDescription(), payload.preflightAcknowledged(),
                        payload.maxPages(), payload.language(), payload.coverLetter(),
                        progress);

        return switch (result) {
            case Result.Ok<GeneratedGeneration> ok -> completed(user, payload, ok.value());
            case Result.Err<GeneratedGeneration> failed -> {
                // Bolum 44.2: the unit was taken when this was queued and no
                // document came out of it. User error or system error, the
                // section refunds both.
                quotas.refund(QuotaSubject.of(user), QuotaMetric.GENERATION);
                yield failed(failed.error());
            }
        };
    }

    private JobOutcome completed(
            UserContext user, GenerationPayload payload, GeneratedGeneration generated) {

        Generation record = persist(user, payload, generated);
        GeneratedDocument document = generated.document();

        // Counts, never content (absolute rule 4). What the terminal SSE event
        // of Bolum 30.6 carries, and what GET /jobs/{id} reads back.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generationId", record.getId().toString());
        result.put("pageCount", document.pageCount());
        if (generated.fitReport() != null) {
            // Bolum 30.6's example carries it and F-008 asked for it: the
            // heading is on the terminal event so the result screen can print
            // it without a second round trip. The counts underneath are on
            // GET /generations/{id} — a level is four characters, a report is
            // not something to push down a stream.
            result.put("matchLevel", generated.fitReport().level().name());
        }
        return JobOutcome.completed(result);
    }

    private Generation persist(
            UserContext user, GenerationPayload payload, GeneratedGeneration generated) {

        GenerationOptions options = generated.options();
        GeneratedDocument document = generated.document();
        SelectionState selection = document.selection();

        var record = new Generation(
                user.userId(),
                generated.profileId(),
                storedOptions(options),
                StoredSelection.of(selection, options.language(), options.customization()),
                engineVersion(options, generated));

        if (!isGeneralMode(payload)) {
            record.recordPosting(
                    payload.jobDescription(),
                    JobDescriptionDigest.of(payload.jobDescription()),
                    generated.posting());
        }
        record.setPageCount(document.pageCount());
        record.setFitReport(generated.fitReport());
        record.setContentSnapshot(RenderedContent.of(document.rendered()));
        // Absent when it was not asked for, and absent when it was asked for
        // and refused — Bolum 34 does not print a letter it could not check,
        // and the CV is what the person came for.
        record.setCoverLetter(generated.coverLetter());
        record.setTrace(trace(generated));

        return records.save(user, record);
    }

    /** Bolum 14.4, minus the fields whose features have not arrived. */
    private static Map<String, Object> storedOptions(GenerationOptions options) {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("templateId", options.customization().baseTemplateId());
        stored.put("templateVersion",
                TemplateRegistry.versionOf(options.customization().baseTemplateId()));
        stored.put("maxPages", options.maxPages());
        stored.put("cvLanguage", options.language());
        stored.put("formats", java.util.List.of("pdf"));
        return stored;
    }

    private static EngineVersion engineVersion(GenerationOptions options, GeneratedGeneration generated) {
        return new EngineVersion(
                EngineVersion.PIPELINE,
                // Bolum 28.4: which of the two weight sets ran. A week of
                // generations scored without vectors otherwise looks exactly
                // like a prompt regression.
                weightsOf(generated),
                options.customization().costKey(),
                generated.promptVersions());
    }

    /**
     * Bolum 14.6, with the phases that are instrumented.
     *
     * <p>A, D and E are absent rather than guessed at: nothing times them
     * today, and a trace carrying a zero would read as "instant" instead of as
     * "unmeasured". They arrive when the phases are instrumented.
     */
    private static Map<String, Object> trace(GeneratedGeneration generated) {
        SelectionState selection = generated.document().selection();

        Map<String, Object> phaseB = new LinkedHashMap<>();
        phaseB.put("weights", weightsOf(generated));

        Map<String, Object> phaseC = new LinkedHashMap<>();
        phaseC.put("selected", selection.selected().size());
        phaseC.put("rejected", selection.rejected().size());
        phaseC.put("usedPt", selection.budget().usedPt());

        Map<String, Object> phaseF = new LinkedHashMap<>();
        phaseF.put("pageCount", generated.document().pageCount());
        phaseF.put("attempts", generated.document().attempts());
        phaseF.put("budgetFactor", generated.document().budgetFactor());

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("B", phaseB);
        trace.put("C", phaseC);
        trace.put("F", phaseF);
        return trace;
    }

    /**
     * Which weight set scored this run, or that none did.
     *
     * <p>General mode has no Faz B at all, and writing "default" would make a
     * run that never compared anything look like one that did.
     */
    private static String weightsOf(GeneratedGeneration generated) {
        if (generated.weights() == null) {
            return "general-mode";
        }
        return generated.weights().usesEmbedding() ? "default" : "without-embedding";
    }

    /**
     * Bolum 30.5 decides retryability from the error, and this is the only
     * place holding it in that form — which is why {@link JobOutcome} carries
     * the answer rather than the queue working it out.
     */
    private JobOutcome failed(PipelineError error) {
        return JobOutcome.failed(
                errors.present(error, pageHeightPt()), JobRetryPolicy.isRetryable(error));
    }

    private static double pageHeightPt() {
        return TemplateRegistry.capacityOf(
                        com.mustafatetik.atomcv.rendering.template.TemplateCustomization.CLASSIC)
                .orElseThrow().pageTextHeightPt();
    }
}
