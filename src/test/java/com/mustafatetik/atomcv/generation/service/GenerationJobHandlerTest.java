package com.mustafatetik.atomcv.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysisPhase;
import com.mustafatetik.atomcv.generation.phases.analysis.JobDescriptionDigest;
import com.mustafatetik.atomcv.generation.pipeline.ErrorPresenter;
import com.mustafatetik.atomcv.generation.pipeline.GeneratedDocument;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.scoring.ScoringWeights;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.jobs.queue.ProgressSink;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Where the queue meets the pipeline (Bolum 30, Bolum 14).
 *
 * <p>Two things are worth proving here and nowhere else: that a generation
 * record describes the run that actually happened rather than a re-derived
 * one, and that the retry decision Bolum 30.5 makes from the error reaches the
 * queue as a boolean.
 */
class GenerationJobHandlerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID PROFILE = UUID.randomUUID();
    private static final String POSTING = "We are hiring a senior backend engineer.";

    private JobSpecificGenerationService generations;
    private CvGenerationService general;
    private GenerationRepository records;
    private com.mustafatetik.atomcv.billing.QuotaService quotas;
    private GenerationJobHandler handler;

    @BeforeEach
    void wireTheMocks() {
        generations = mock(JobSpecificGenerationService.class);
        records = mock(GenerationRepository.class);
        general = mock(CvGenerationService.class);
        quotas = mock(com.mustafatetik.atomcv.billing.QuotaService.class);
        handler = new GenerationJobHandler(
                generations, general, records, quotas, new ErrorPresenter());
        when(records.save(any(), any())).thenAnswer(call -> call.getArgument(1));
    }

    @Test
    void ithandlesGenerationJobs() {
        assertThat(handler.type()).isEqualTo(JobType.GENERATION);
    }

    // ── the record ───────────────────────────────────────────────────────

    @Test
    void asuccessfulRunIsWrittenDownAndItsIdComesBack() {
        when(generations.generateForJob(any(), anyString(), anyBoolean(), any(), any(), anyBoolean(), any()))
                .thenReturn(Result.ok(generated()));

        JobOutcome outcome = handler.handle(job(), ProgressSink.NONE);

        var saved = ArgumentCaptor.forClass(Generation.class);
        verify(records).save(any(), saved.capture());
        verify(quotas, never()).refund(any(), any());
        assertThat(outcome).isInstanceOfSatisfying(JobOutcome.Completed.class,
                done -> assertThat(done.result())
                        .containsEntry("generationId", saved.getValue().getId().toString())
                        .containsEntry("pageCount", 1));
    }

    /**
     * The snapshot is what a download re-renders from, so it has to carry how
     * to draw as well as what to draw (EK D.6.3). An id pointing at a
     * customization row would resolve to nothing — there are no rows yet.
     */
    @Test
    void thesnapshotCarriesEnoughToDrawThePageAgain() {
        when(generations.generateForJob(any(), anyString(), anyBoolean(), any(), any(), anyBoolean(), any()))
                .thenReturn(Result.ok(generated()));

        handler.handle(job(), ProgressSink.NONE);

        var saved = ArgumentCaptor.forClass(Generation.class);
        verify(records).save(any(), saved.capture());
        var snapshot = saved.getValue().getSelectionState();
        assertThat(snapshot.language()).isEqualTo("en");
        assertThat(snapshot.customization()).isEqualTo(TemplateCustomization.CLASSIC);
        assertThat(snapshot.selected()).hasSize(1);
        assertThat(snapshot.budget().usedPt()).isEqualTo(120.0);
    }

    /** The same hash the analysis cache keys on, so the two can be matched. */
    @Test
    void thepostingIsRecordedWithTheHashTheCacheUses() {
        when(generations.generateForJob(any(), anyString(), anyBoolean(), any(), any(), anyBoolean(), any()))
                .thenReturn(Result.ok(generated()));

        handler.handle(job(), ProgressSink.NONE);

        var saved = ArgumentCaptor.forClass(Generation.class);
        verify(records).save(any(), saved.capture());
        assertThat(saved.getValue().getJobDescription()).isEqualTo(POSTING);
        assertThat(saved.getValue().getJdHash()).isEqualTo(JobDescriptionDigest.of(POSTING));
        assertThat(saved.getValue().getJdAnalysis()).isNotNull();
    }

    /**
     * Bolum 28.4: a week of generations scored without vectors is otherwise
     * indistinguishable from a prompt regression, and the prompt version has
     * to be the one that ran rather than the configured default (Bolum 53.3).
     */
    @Test
    void theengineVersionNamesWhatActuallyRan() {
        when(generations.generateForJob(any(), anyString(), anyBoolean(), any(), any(), anyBoolean(), any()))
                .thenReturn(Result.ok(generatedWith(ScoringWeights.WITHOUT_EMBEDDING, "v7")));

        handler.handle(job(), ProgressSink.NONE);

        var saved = ArgumentCaptor.forClass(Generation.class);
        verify(records).save(any(), saved.capture());
        var engine = saved.getValue().getEngineVersion();
        assertThat(engine.scoringWeights()).isEqualTo("without-embedding");
        assertThat(engine.template()).isEqualTo(TemplateCustomization.CLASSIC.costKey());
        assertThat(engine.promptVersions())
                .containsEntry(JobAnalysisPhase.PROMPT_ID, "v7");
    }

    /**
     * A zero would read as "instant" rather than as "unmeasured", so the
     * phases nothing times are absent instead.
     */
    @Test
    void thetraceCarriesOnlyThePhasesThatAreInstrumented() {
        when(generations.generateForJob(any(), anyString(), anyBoolean(), any(), any(), anyBoolean(), any()))
                .thenReturn(Result.ok(generated()));

        handler.handle(job(), ProgressSink.NONE);

        var saved = ArgumentCaptor.forClass(Generation.class);
        verify(records).save(any(), saved.capture());
        assertThat(saved.getValue().getTrace())
                .containsOnlyKeys("B", "C", "F");
    }

    // ── failure ──────────────────────────────────────────────────────────

    /** Bolum 30.5: the world outside may have changed by the next attempt. */
    @Test
    void aprovideroutageComesBackRetryableAndWritesNoRecord() {
        when(generations.generateForJob(any(), anyString(), anyBoolean(), any(), any(), anyBoolean(), any()))
                .thenReturn(Result.err(
                        new PipelineError.AllProvidersUnavailable(List.of("openrouter"))));

        JobOutcome outcome = handler.handle(job(), ProgressSink.NONE);

        assertThat(outcome).isInstanceOfSatisfying(JobOutcome.Failed.class, failed -> {
            assertThat(failed.retryable()).isTrue();
            assertThat(failed.error().code()).isEqualTo(ErrorCode.ALL_PROVIDERS_UNAVAILABLE);
        });
        // selection_state is what a row is for, and there is none.
        verify(records, never()).save(any(), any());
        // Bolum 44.2: no document came out, so the unit goes back.
        verify(quotas).refund(any(), eq(com.mustafatetik.atomcv.billing.QuotaMetric.GENERATION));
    }

    /** The next attempt reads the same thin profile and reaches the same answer. */
    @Test
    void athinProfileComesBackFinal() {
        when(generations.generateForJob(any(), anyString(), anyBoolean(), any(), any(), anyBoolean(), any()))
                .thenReturn(Result.err(
                        new PipelineError.InsufficientProfile(10, List.of("atoms"))));

        JobOutcome outcome = handler.handle(job(), ProgressSink.NONE);

        assertThat(outcome).isInstanceOfSatisfying(JobOutcome.Failed.class,
                failed -> assertThat(failed.retryable()).isFalse());
    }

    /**
     * Bolum 9's anonymous flow is Stage 3. Inventing an owner would write a row
     * belonging to nobody, which every scoped read would then refuse to
     * anybody — including the person who asked for it.
     */
    @Test
    void anAnonymousJobIsRefusedRatherThanGivenAnOwner() {
        var anonymous = new Job(JobType.GENERATION, null, payload(), Instant.EPOCH);

        JobOutcome outcome = handler.handle(anonymous, ProgressSink.NONE);

        assertThat(outcome).isInstanceOfSatisfying(JobOutcome.Failed.class, failed -> {
            assertThat(failed.retryable()).isFalse();
            assertThat(failed.error().code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        });
        verify(generations, never()).generateForJob(any(), any(), anyBoolean(), any(), any(), anyBoolean(), any());
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private static Job job() {
        return new Job(JobType.GENERATION, USER, payload(), Instant.EPOCH);
    }

    private static Map<String, Object> payload() {
        return new GenerationPayload(POSTING, false, 1, "en", false).toMap();
    }

    private static GeneratedGeneration generated() {
        return generatedWith(ScoringWeights.DEFAULT, "v1");
    }

    private static GeneratedGeneration generatedWith(
            ScoringWeights weights, String promptVersion) {

        var selection = new SelectionState(
                List.of(new SelectionState.SelectedAtom(
                        UUID.randomUUID(), UUID.randomUUID(), 0.8, 27.7, false)),
                List.of(new SelectionState.RejectedAtom(
                        UUID.randomUUID(), 0.1, SelectionState.RejectionReason.BUDGET)),
                new SelectionState.BudgetBreakdown(648.0, 142.0, 506.0, 120.0));

        return new GeneratedGeneration(
                PROFILE,
                posting(),
                new GenerationOptions(1, "en", TemplateCustomization.CLASSIC),
                weights,
                Map.of(JobAnalysisPhase.PROMPT_ID, promptVersion),
                new GeneratedDocument("%PDF".getBytes(StandardCharsets.UTF_8), 1, selection,
                        new com.mustafatetik.atomcv.rendering.model.RenderRequest(
                                new com.mustafatetik.atomcv.rendering.model.RenderRequest
                                        .ProfileHeader("Ada", "", List.of()),
                                List.of(), TemplateCustomization.CLASSIC,
                                java.util.Locale.ENGLISH),
                        1, 1.0));
    }

    private static JobAnalysis posting() {
        return new JobAnalysis(
                new JobAnalysis.Role("Senior Backend Engineer", JobAnalysis.Seniority.SENIOR,
                        "fintech", JobAnalysis.EmploymentType.FULL_TIME,
                        JobAnalysis.WorkMode.REMOTE),
                new JobAnalysis.Company("Acme", JobAnalysis.SizeHint.SCALEUP),
                List.of(new JobAnalysis.Skill("Go", "go", JobAnalysis.Importance.CRITICAL)),
                List.of(), List.of("scale payment systems"), List.of("distributed systems"),
                new JobAnalysis.ExperienceYears(5, null),
                List.of("en"), "technical", "en", 0.94, List.of());
    }
}
