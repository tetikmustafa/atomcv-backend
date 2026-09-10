package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.billing.QuotaMetric;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.generation.domain.EngineVersion;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.RenderedContent;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.phases.analysis.JobDescriptionDigest;
import com.mustafatetik.atomcv.generation.pipeline.ErrorPresenter;
import com.mustafatetik.atomcv.generation.pipeline.GeneratedDocument;
import com.mustafatetik.atomcv.generation.repository.AnonymousGenerations;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.profile.repository.AnonymousProfiles;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.generation.rewrite.RewriteTally;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobHandler;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobRetryPolicy;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.jobs.queue.ProgressSink;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
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
    private final AnonymousGenerations anonymousRecords;
    private final AnonymousProfiles anonymous;
    private final ProfileResolver profiles;
    private final QuotaService quotas;
    private final ErrorPresenter errors;

    GenerationJobHandler(JobSpecificGenerationService generations, CvGenerationService general,
            GenerationRepository records, AnonymousGenerations anonymousRecords,
            AnonymousProfiles anonymous, ProfileResolver profiles, QuotaService quotas,
            ErrorPresenter errors) {

        this.anonymousRecords = anonymousRecords;
        this.anonymous = anonymous;
        this.profiles = profiles;
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
        GenerationPayload payload = GenerationPayload.from(job.getPayload());
        Result<GenerationSubject> resolved = subjectFor(job);
        if (resolved instanceof Result.Err<GenerationSubject> refused) {
            return failed(refused.error());
        }
        GenerationSubject subject = resolved.orElseThrow();

        // Bolum 19.4: no posting means no Faz A and no Faz B. Everything from
        // selection onwards is the same code, which is what separating scoring
        // from selection bought.
        //
        // General mode is still account-only. Nothing refuses it here because
        // nothing offers it: § 35.7 gives an anonymous session one language and
        // the flow it was built for is "against this posting". It arrives when
        // somebody asks for it, not before.
        Result<GeneratedGeneration> result = isGeneralMode(payload)
                ? general.generateGeneralCv(UserContext.of(job.getOwnerId()),
                        payload.maxPages(), payload.language(), progress)
                : generations.generateForJob(
                        subject, payload.jobDescription(), payload.preflightAcknowledged(),
                        payload.maxPages(), payload.language(), payload.coverLetter(),
                        progress, job.getId());

        return switch (result) {
            case Result.Ok<GeneratedGeneration> ok -> completed(subject, payload, ok.value());
            case Result.Err<GeneratedGeneration> failed -> {
                // Bolum 44.2: the unit was taken when this was queued and no
                // document came out of it. User error or system error, the
                // section refunds both -- to the subject that paid, which the
                // payload carries because the worker has no request to read an
                // address from.
                quotas.refund(payload.allowance(), QuotaMetric.GENERATION);
                yield failed(failed.error());
            }
        };
    }

    /**
     * Who this job is for, resolved from the job rather than assumed.
     *
     * <p><strong>The one place the two kinds of caller diverge.</strong> An
     * account's profile is created on first use, so resolving it always
     * succeeds. An anonymous session's has to be there already -- the import or
     * the editor made it -- and if it is not, the session ended between the
     * request and the worker and there is nothing to generate from. That is a
     * refusal with an honest reason rather than an empty CV.
     */
    private Result<GenerationSubject> subjectFor(Job job) {
        UUID userId = job.getOwnerId();
        if (userId != null) {
            UserContext user = UserContext.of(userId);
            return Result.ok(GenerationSubject.account(profiles.owned(user), userId));
        }

        String session = job.getAnonSessionId();
        if (session == null) {
            // Neither an account nor a session: a row that should not exist.
            log.error("A generation belonging to nobody reached the queue; job {}", job.getId());
            return Result.err(new PipelineError.SessionEnded());
        }

        ProfileRef ref = ProfileRef.ephemeral(AnonymousSessionId.of(session));
        return anonymous.find(ref)
                .<Result<GenerationSubject>>map(profile -> Result.ok(GenerationSubject.anonymous(
                        new ProfileResolver.OwnedProfile(profile, ref))))
                .orElseGet(() -> {
                    // Counts and ids, never content (absolute rule 4).
                    log.info("An anonymous generation outlived its profile; job {}", job.getId());
                    return Result.err(new PipelineError.SessionEnded());
                });
    }

    private JobOutcome completed(
            GenerationSubject subject, GenerationPayload payload,
            GeneratedGeneration generated) {

        Generation record = persist(subject, payload, generated);
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
            GenerationSubject subject, GenerationPayload payload,
            GeneratedGeneration generated) {

        GenerationOptions options = generated.options();
        GeneratedDocument document = generated.document();
        SelectionState selection = document.selection();

        // Null for an anonymous session, which the column has allowed since V1.
        // Nothing else about the row differs and nothing has to expire it:
        // generations.profile_id cascades from profiles, so this dies with the
        // profile the sweep deletes (§ 51.6.1).
        var record = new Generation(
                subject.userId(),
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
        // V11. The snapshot above cannot stand in for it: it is the render, and
        // Bolum 22.2 built the render to carry no atom ids. Faz G reads this
        // one back so an edit keeps the sentences it did not touch.
        record.setRewrittenContent(document.rewritten());
        // Absent when it was not asked for, and absent when it was asked for
        // and refused — Bolum 34 does not print a letter it could not check,
        // and the CV is what the person came for.
        record.setCoverLetter(generated.coverLetter());
        record.setTrace(trace(generated));

        // The one write that differs, and only in which door it goes through: a
        // generation with no owner cannot pass a user-scoped save, and the row
        // is already filed under the profile it belongs to.
        return subject.isAnonymous()
                ? anonymousRecords.save(subject.profile(), record)
                : records.save(UserContext.of(subject.userId()), record);
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
     * <p>A and E are absent rather than guessed at: nothing times them today,
     * and a trace carrying a zero would read as "instant" instead of as
     * "unmeasured". They arrive when the phases are instrumented.
     *
     * <p>C carries its budget, which Bolum 14.6 does not ask for. It is here
     * because the abridged version could not answer the one question it gets
     * asked: a page that came out under-filled recorded {@code "rejected": 13}
     * and nothing about how much room those thirteen were turned away from, so
     * telling a selection bug from a budget bug meant reading
     * {@code selection_state} out of the database by hand.
     */
    private static Map<String, Object> trace(GeneratedGeneration generated) {
        SelectionState selection = generated.document().selection();

        Map<String, Object> phaseB = new LinkedHashMap<>();
        phaseB.put("weights", weightsOf(generated));

        Map<String, Object> phaseC = new LinkedHashMap<>();
        phaseC.put("selected", selection.selected().size());
        phaseC.put("rejected", selection.rejected().size());
        phaseC.put("rejectionReasons", rejectionReasons(selection));
        phaseC.put("pinnedCostPt", pinnedCostPt(selection));
        phaseC.put("budget", budget(selection.budget()));

        // Faz D. Always written, general mode included, because zero is a fact
        // there too and omitting it would put "no posting to write towards" and
        // "not instrumented" behind the same silence — which is the confusion
        // A and E are absent to avoid. What separates the two cases is B:
        // `weights` reads "general-mode" where there was no posting at all, and
        // names a weight set where there was one and nothing came back.
        RewriteTally rewrites = generated.rewriteTally();
        Map<String, Object> phaseD = new LinkedHashMap<>();
        phaseD.put("rewritten", generated.document().rewrittenAtoms());
        // Bolum 14.6's rejectReasons, and the two counts it takes to read them.
        // `rewritten: 0` on its own has four causes with four different fixes —
        // nothing was a candidate, nothing came back, everything came back and
        // was refused, or the phase never ran at all — and the page looks the
        // same in all four. `calls` separates the first two from the last two
        // and `rejectReasons` separates those; `unreachable` is the provider
        // chain's share, which is not a prompt problem and must not be counted
        // as one.
        phaseD.put("calls", rewrites.callsByPrompt());
        phaseD.put("rejectReasons", rejectReasons(rewrites));
        phaseD.put("unreachable", rewrites.unreachable());

        Map<String, Object> phaseF = new LinkedHashMap<>();
        phaseF.put("pageCount", generated.document().pageCount());
        phaseF.put("attempts", generated.document().attempts());
        phaseF.put("budgetFactor", generated.document().budgetFactor());

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("B", phaseB);
        trace.put("C", phaseC);
        trace.put("D", phaseD);
        trace.put("F", phaseF);
        return trace;
    }

    /**
     * How many atoms each reason turned away.
     *
     * <p>Walked in the enum's own order, not the rejections': a map built from
     * whatever order the list happened to be in reaches a JSONB column
     * differently on two runs of the same input, and Faz C is supposed to be
     * the part of this that never varies.
     */
    private static Map<String, Integer> rejectionReasons(SelectionState selection) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SelectionState.RejectionReason reason : SelectionState.RejectionReason.values()) {
            long tally = selection.rejected().stream()
                    .filter(atom -> atom.reason() == reason)
                    .count();
            if (tally > 0) {
                counts.put(reason.name(), (int) tally);
            }
        }
        return counts;
    }

    /**
     * How many refusals each of Bolum 21.6's issues accounts for.
     *
     * <p>{@link RewriteTally} already holds these in an {@code EnumMap}, so the
     * order is the enum's and not the answers' — the same determinism Faz C's
     * reasons are walked for, and for the same reason: this lands in a JSONB
     * column and two runs of one input must store one trace.
     *
     * <p>One attempt can appear under several issues. A rewrite that was both
     * too long and short a number is one refusal and two reasons, and the
     * question this answers is "what is going wrong", not "how many times".
     */
    private static Map<String, Integer> rejectReasons(RewriteTally tally) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        tally.refusals().forEach((issue, count) -> counts.put(issue.name(), count));
        return counts;
    }

    /** What the locks alone came to, before anything competed for the rest. */
    private static double pinnedCostPt(SelectionState selection) {
        return selection.selected().stream()
                .filter(SelectionState.SelectedAtom::forcedByLock)
                .mapToDouble(SelectionState.SelectedAtom::renderCostPt)
                .sum();
    }

    private static Map<String, Object> budget(SelectionState.BudgetBreakdown breakdown) {
        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("totalPt", breakdown.totalPt());
        budget.put("fixedPt", breakdown.fixedPt());
        budget.put("freePt", breakdown.freePt());
        budget.put("usedPt", breakdown.usedPt());
        budget.put("remainingPt", breakdown.remainingPt());
        return budget;
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
