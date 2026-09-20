package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.billing.QuotaMetric;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.generation.domain.EngineVersion;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.RenderedContent;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.phases.analysis.JobDescriptionDigest;
import com.mustafatetik.atomcv.generation.phases.edit.EditPlan;
import com.mustafatetik.atomcv.generation.pipeline.ErrorPresenter;
import com.mustafatetik.atomcv.generation.pipeline.GeneratedDocument;
import com.mustafatetik.atomcv.generation.repository.AnonymousGenerations;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.profile.repository.AnonymousProfiles;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.generation.rewrite.RewriteTally;
import com.mustafatetik.atomcv.generation.selection.GenerationDirectives;
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
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The queued half of a job-specific generation.
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
    private final GenerationRerunService reruns;
    private final NaturalLanguageEditService language;
    private final ErrorPresenter errors;
    private final MeterRegistry meters;

    /** 48.3's "estimate usage rate": the source tag is the ratio. */
    static final String SELECTION_COSTS = "generation.selection.costs";

    /**
     * 48.3's "budget fill rate", and the closest thing to a direct reading of
     * what this product promises.
     *
     * <p>A page under a limit is the guarantee; a page half empty under it is
     * the guarantee kept and the point missed, and nothing published the
     * difference. {@code generation.budget.overshoot} answers the opposite
     * question — a selection that did not fit — and a deployment where every
     * CV fills sixty percent of its page looks identical to a healthy one
     * through it.
     */
    static final String BUDGET_FILL = "generation.budget.fill";

    GenerationJobHandler(JobSpecificGenerationService generations, CvGenerationService general,
            GenerationRepository records, AnonymousGenerations anonymousRecords,
            AnonymousProfiles anonymous, ProfileResolver profiles, QuotaService quotas,
            GenerationRerunService reruns, NaturalLanguageEditService language,
            ErrorPresenter errors, MeterRegistry meters) {

        this.meters = meters;

        this.anonymousRecords = anonymousRecords;
        this.anonymous = anonymous;
        this.profiles = profiles;
        this.quotas = quotas;
        this.generations = generations;
        this.general = general;
        this.records = records;
        this.reruns = reruns;
        this.language = language;
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
        // Asked before the payload is parsed as anything else.
        // GenerationPayload.from is forgiving about absence, so an edit read
        // as a generation would come out as general CV mode and quietly
        // rebuild the document from scratch -- same person, same profile, none
        // of their edits, and no error anywhere to say so.
        if (SelectionEditPayload.isEdit(job.getPayload())) {
            return handleEdit(job, progress);
        }

        GenerationPayload payload = GenerationPayload.from(job.getPayload());
        Result<GenerationSubject> resolved = subjectFor(job);
        if (resolved instanceof Result.Err<GenerationSubject> refused) {
            return failed(refused.error());
        }
        GenerationSubject subject = resolved.orElseThrow();

        // No posting means no Faz A and no Faz B. Everything from selection
        // onwards is the same code, which is what separating scoring from
        // selection bought.
        //
        // General mode is still account-only. Nothing refuses it here because
        // nothing offers it: an anonymous session gets one language and the
        // flow it was built for is "against this posting". It arrives when
        // somebody asks for it, not before.
        Result<GeneratedGeneration> result = isGeneralMode(payload)
                ? general.generateGeneralCv(UserContext.of(job.getOwnerId()),
                        payload.maxPages(), payload.language(), progress)
                : generations.generateForJob(
                        subject, payload.jobDescription(), payload.preflightAcknowledged(),
                        payload.maxPages(), payload.language(), payload.coverLetter(),
                        payload.customizationId(),
                        // The directive belongs to this run, never to the
                        // cached analysis the posting hash keys.
                        GenerationDirectives.steering(
                                payload.emphasize(), payload.freeformNote()),
                        progress, job.getId());

        return switch (result) {
            case Result.Ok<GeneratedGeneration> ok -> completed(subject, payload, ok.value());
            case Result.Err<GeneratedGeneration> failed -> {
                // The unit was taken when this was queued and no document came
                // out of it. User error or system error, the section refunds
                // both -- to the subject that paid, which the payload carries
                // because the worker has no request to read an address from.
                quotas.refund(payload.allowance(), QuotaMetric.GENERATION);
                yield failed(failed.error());
            }
        };
    }

    /**
     * Faz G's manual toggle, once the queue has reached it.
     *
     * <p>Nothing is refunded on failure and nothing was consumed: a hand edit
     * re-runs selection, the renderer and the compiler and asks no model
     * anything, so there is no allowance in the payload to give back.
     *
     * <p><strong>The parent is marked superseded last, and only if a document
     * came out.</strong> A run that failed after the row was flipped would
     * leave the person with a history whose newest entry is retired and no
     * replacement for it — every screen would show them a CV they had already
     * moved on from, and nothing would say why.
     */
    private JobOutcome handleEdit(Job job, ProgressSink progress) {
        SelectionEditPayload payload = SelectionEditPayload.from(job.getPayload());
        Result<GenerationSubject> resolved = subjectFor(job);
        if (resolved instanceof Result.Err<GenerationSubject> refused) {
            return failed(refused.error());
        }
        GenerationSubject subject = resolved.orElseThrow();

        Optional<Generation> found = subject.isAnonymous()
                ? anonymousRecords.findById(subject.profile(), payload.parentGenerationId())
                : records.findById(
                        UserContext.of(subject.userId()), payload.parentGenerationId());
        if (found.isEmpty()) {
            // Between the request and the worker the row went: an account
            // deleted, an anonymous session swept. Absolute rule 3 is why this
            // is a scoped read and why "gone" and "somebody else's" are the
            // same answer here.
            log.info("The generation an edit was queued against is gone; job {}", job.getId());
            return failed(new PipelineError.SessionEnded());
        }
        Generation parent = found.get();

        // A sentence has to be read before it can be applied, and that reading
        // is the only thing an edit ever pays for. A hand toggle arrives with
        // its ids already decided and skips it entirely.
        GenerationDirectives directives = payload.directives();
        if (payload.isNaturalLanguage()) {
            progress.report(GenerationPhase.ANALYSING.at(15));
            Result<EditPlan> read = language.plan(
                    subject, parent, payload.instruction(), job.getId());
            if (read instanceof Result.Err<EditPlan> refused) {
                // Refunded here rather than at the end: the parse is what was
                // paid for, and a sentence that named no line got nothing for
                // it.
                refund(payload);
                return failed(refused.error());
            }
            directives = directives.and(read.orElseThrow().asDirectives());
        }

        Result<GeneratedGeneration> result =
                reruns.rerun(subject, parent, directives, progress);

        GenerationDirectives applied = directives;
        return switch (result) {
            case Result.Ok<GeneratedGeneration> ok ->
                    completedEdit(subject, parent, applied, ok.value());
            case Result.Err<GeneratedGeneration> failed -> {
                refund(payload);
                yield failed(failed.error());
            }
        };
    }

    /**
     * A hand toggle took nothing, so there is nothing to give back; a
     * natural-language edit paid for its parse when it was queued.
     */
    private void refund(SelectionEditPayload payload) {
        if (payload.allowance() != null) {
            quotas.refund(payload.allowance(), QuotaMetric.GENERATION);
        }
    }

    private JobOutcome completedEdit(
            GenerationSubject subject, Generation parent,
            GenerationDirectives directives, GeneratedGeneration generated) {

        Generation record = persistEdit(subject, parent, directives, generated);
        GeneratedDocument document = generated.document();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generationId", record.getId().toString());
        result.put("pageCount", document.pageCount());
        if (generated.fitReport() != null) {
            result.put("matchLevel", generated.fitReport().level().name());
        }
        // The row the screen was looking at, so a client holding the old id
        // knows which one it has been replaced by without re-reading history.
        result.put("supersededGenerationId", parent.getId().toString());
        return JobOutcome.completed(result);
    }

    private Generation persistEdit(
            GenerationSubject subject, Generation parent,
            GenerationDirectives directives, GeneratedGeneration generated) {

        GenerationOptions options = generated.options();
        GeneratedDocument document = generated.document();
        var record = new Generation(
                subject.userId(),
                generated.profileId(),
                parent.getOptions(),
                StoredSelection.of(document.selection(), options.language(),
                        options.customization()),
                // Faz B did not run, so the weight set is the parent's rather
                // than a fresh reading. Saying "general-mode" here -- which is
                // what a null weights object means -- would file a job-specific
                // CV under the one mode it was not made in.
                new EngineVersion(EngineVersion.PIPELINE,
                        parent.getEngineVersion().scoringWeights(),
                        options.customization().costKey(),
                        parent.getEngineVersion().promptVersions()));

        if (parent.getJobDescription() != null) {
            record.recordPosting(parent.getJobDescription(), parent.getJdHash(),
                    parent.getJdAnalysis());
        }
        // Everything asked for so far, not just this edit: the payload
        // carries the sum, and the next edit merges onto this row.
        record.setDirectives(directives.asMap());
        record.supersede(parent.getId());
        record.setPageCount(document.pageCount());
        record.setFitReport(generated.fitReport());
        record.setContentSnapshot(RenderedContent.of(document.rendered()));
        record.setRewrittenContent(document.rewritten());
        record.setCoverLetter(generated.coverLetter());
        record.setTrace(trace(generated));
        priced(generated);

        Generation saved = subject.isAnonymous()
                ? anonymousRecords.save(subject.profile(), record)
                : records.save(UserContext.of(subject.userId()), record);

        parent.markSuperseded();
        if (subject.isAnonymous()) {
            anonymousRecords.save(subject.profile(), parent);
        } else {
            records.save(UserContext.of(subject.userId()), parent);
        }
        return saved;
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
        // carries, and what GET /jobs/{id} reads back.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generationId", record.getId().toString());
        result.put("pageCount", document.pageCount());
        if (generated.fitReport() != null) {
            // The example carries it and F-008 asked for it: the heading is on
            // the terminal event so the result screen can print it without a
            // second round trip. The counts underneath are on GET
            // /generations/{id} — a level is four characters, a report is not
            // something to push down a stream.
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

        // Null for an anonymous session, which the column has allowed since
        // V1. Nothing else about the row differs and nothing has to expire it:
        // generations.profile_id cascades from profiles, so this dies with the
        // profile the sweep deletes.
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
        // V11. The snapshot above cannot stand in for it: it is the render,
        // and the render carries no atom ids. Faz G reads this one back so an
        // edit keeps the sentences it did not touch.
        record.setRewrittenContent(document.rewritten());
        // Absent when it was not asked for, and absent when it was asked for
        // and refused — a letter that could not be checked is not printed, and
        // the CV is what the person came for.
        record.setCoverLetter(generated.coverLetter());
        record.setTrace(trace(generated));
        priced(generated);

        // The one write that differs, and only in which door it goes through: a
        // generation with no owner cannot pass a user-scoped save, and the row
        // is already filed under the profile it belongs to.
        return subject.isAnonymous()
                ? anonymousRecords.save(subject.profile(), record)
                : records.save(UserContext.of(subject.userId()), record);
    }

    /** Minus the fields whose features have not arrived. */
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
                // Which of the two weight sets ran. A week of generations
                // scored without vectors otherwise looks exactly like a prompt
                // regression.
                weightsOf(generated),
                options.customization().costKey(),
                generated.promptVersions());
    }

    /**
     * With the phases that are instrumented.
     *
     * <p>A and E are absent rather than guessed at: nothing times them today,
     * and a trace carrying a zero would read as "instant" instead of as
     * "unmeasured". They arrive when the phases are instrumented.
     *
     * <p>C carries its budget, which the trace was not asked for. It is here
     * because the abridged version could not answer the one question it gets
     * asked: a page that came out under-filled recorded {@code "rejected": 13}
     * and nothing about how much room those thirteen were turned away from, so
     * telling a selection bug from a budget bug meant reading {@code
     * selection_state} out of the database by hand.
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
        // Sections 20.4 and 26.5 both promise this counter and neither was
        // writing it: the number was computed, logged once at INFO and
        // dropped. It matters here of all places -- a page that came out
        // under-filled is read months later out of this column, and "the
        // measurement job had not reached this profile" and "selection is
        // wrong" look identical without it.
        phaseC.put("estimatedAtoms", generated.selectionCosts().estimated());

        // Faz D. Always written, general mode included, because zero is a fact
        // there too and omitting it would put "no posting to write towards" and
        // "not instrumented" behind the same silence — which is the confusion
        // A and E are absent to avoid. What separates the two cases is B:
        // `weights` reads "general-mode" where there was no posting at all, and
        // names a weight set where there was one and nothing came back.
        RewriteTally rewrites = generated.rewriteTally();
        Map<String, Object> phaseD = new LinkedHashMap<>();
        phaseD.put("rewritten", generated.document().rewrittenAtoms());
        // The rejectReasons, and the two counts it takes to read them.
        // `rewritten: 0` on its own has four causes with four different fixes
        // — nothing was a candidate, nothing came back, everything came back
        // and was refused, or the phase never ran at all — and the page looks
        // the same in all four. `calls` separates the first two from the last
        // two and `rejectReasons` separates those; `unreachable` is the
        // provider chain's share, which is not a prompt problem and must not
        // be counted as one.
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
     * The estimate usage rate, as two series rather than one.
     *
     * <p>Section 48.3 asks for it by name and nothing published it. A single
     * counter of estimates would not answer the question either: eleven
     * estimates is a lot on a small profile and nothing on a large one, so the
     * tag carries both halves and the ratio is the reading.
     *
     * <p>Here rather than in the builder because this is the one place every
     * persisted generation passes through, whichever of the three services
     * made it — and a meter incremented in three places is a meter that
     * eventually gets incremented in two.
     */
    private void priced(GeneratedGeneration generated) {
        var costs = generated.selectionCosts();
        int measured = costs.costed() - costs.estimated();
        if (costs.estimated() > 0) {
            meters.counter(SELECTION_COSTS, "source", "estimated").increment(costs.estimated());
        }
        if (measured > 0) {
            meters.counter(SELECTION_COSTS, "source", "measured").increment(measured);
        }

        var budget = generated.document().selection().budget();
        if (budget.freePt() > 0) {
            // Of the free budget rather than of the page: the fixed cost is
            // the heading and the section furniture, which no selection
            // decides and every CV pays. A share of the whole page would move
            // when somebody shortened their name.
            meters.summary(BUDGET_FILL).record(budget.usedPt() / budget.freePt());
        }
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
     * How many refusals each of the issues accounts for.
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
     * Retryability is decided from the error, and this is the only place
     * holding it in that form — which is why {@link JobOutcome} carries the
     * answer rather than the queue working it out.
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
