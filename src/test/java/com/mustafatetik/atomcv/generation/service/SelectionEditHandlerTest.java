package com.mustafatetik.atomcv.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.generation.domain.EngineVersion;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.GenerationStatus;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.pipeline.ErrorPresenter;
import com.mustafatetik.atomcv.generation.pipeline.GeneratedDocument;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.rewrite.RewriteTally;
import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.selection.GenerationDirectives;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.jobs.queue.ProgressSink;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * What the queue does with a hand edit (Bolum 24.4).
 *
 * <p>Two things are proved here and nowhere else. The row an edit writes is a
 * <em>new</em> generation pointing at the one it replaced — not an update, so
 * the CV that was sent to an employer stays downloadable. And the parent is
 * retired <strong>after</strong> the document exists: a run that failed with
 * the flag already flipped would leave a person whose newest CV is marked
 * replaced and has no replacement.
 */
class SelectionEditHandlerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID PARENT = UUID.randomUUID();
    private static final UUID EXCLUDED = UUID.randomUUID();

    private GenerationRepository records;
    private GenerationRerunService reruns;
    private GenerationJobHandler handler;
    private Generation parent;

    @BeforeEach
    void wireTheMocks() {
        records = mock(GenerationRepository.class);
        reruns = mock(GenerationRerunService.class);

        var profiles = mock(ProfileResolver.class);
        when(profiles.owned(any())).thenAnswer(call -> {
            UserContext acting = call.getArgument(0);
            var profile = new Profile(acting.userId());
            return new ProfileResolver.OwnedProfile(profile,
                    ProfileRef.persistent(acting, profile.getId(), acting.userId()));
        });

        handler = new GenerationJobHandler(
                mock(JobSpecificGenerationService.class), mock(CvGenerationService.class),
                records,
                mock(com.mustafatetik.atomcv.generation.repository.AnonymousGenerations.class),
                mock(com.mustafatetik.atomcv.profile.repository.AnonymousProfiles.class),
                profiles, mock(com.mustafatetik.atomcv.billing.QuotaService.class),
                reruns, new ErrorPresenter());

        parent = parentGeneration();
        when(records.findById(any(), any())).thenReturn(Optional.of(parent));
        when(records.save(any(), any())).thenAnswer(call -> call.getArgument(1));
    }

    @Test
    void anEditWritesANewRowPointingAtTheOneItReplaced() {
        when(reruns.rerun(any(), any(), any(), any())).thenReturn(Result.ok(rerun()));

        handler.handle(editJob(), ProgressSink.NONE);

        Generation written = firstSaved();
        assertThat(written.getId()).isNotEqualTo(PARENT);
        assertThat(written.getParentGenerationId()).isEqualTo(PARENT);
    }

    @Test
    void theEditedGenerationIsRetired() {
        when(reruns.rerun(any(), any(), any(), any())).thenReturn(Result.ok(rerun()));

        handler.handle(editJob(), ProgressSink.NONE);

        assertThat(parent.getStatus()).isEqualTo(GenerationStatus.SUPERSEDED);
    }

    /**
     * The guard that matters: a failed re-run must leave the history exactly
     * as it was, because the parent is still the person's current CV.
     */
    @Test
    void afailedEditRetiresNothingAndWritesNothing() {
        when(reruns.rerun(any(), any(), any(), any()))
                .thenReturn(Result.err(new PipelineError.PageLimitExceeded(2, 1)));

        JobOutcome outcome = handler.handle(editJob(), ProgressSink.NONE);

        assertThat(outcome).isInstanceOf(JobOutcome.Failed.class);
        assertThat(parent.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        verify(records, never()).save(any(), any());
    }

    @Test
    void theNewRowCarriesEveryDirectiveAskedForSoFar() {
        when(reruns.rerun(any(), any(), any(), any())).thenReturn(Result.ok(rerun()));

        handler.handle(editJob(), ProgressSink.NONE);

        assertThat(GenerationDirectives.fromMap(firstSaved().getDirectives()).excludes(EXCLUDED))
                .isTrue();
    }

    /**
     * Faz B did not run, so the weight set is the parent's. A null weights
     * object reads as "general-mode" through the shared path, which would file
     * a CV written against a posting under the one mode it was not made in.
     */
    @Test
    void theEngineVersionKeepsTheWeightSetThatScoredIt() {
        when(reruns.rerun(any(), any(), any(), any())).thenReturn(Result.ok(rerun()));

        handler.handle(editJob(), ProgressSink.NONE);

        assertThat(firstSaved().getEngineVersion().scoringWeights()).isEqualTo("default");
    }

    @Test
    void theResultNamesTheGenerationThatWasReplaced() {
        when(reruns.rerun(any(), any(), any(), any())).thenReturn(Result.ok(rerun()));

        JobOutcome outcome = handler.handle(editJob(), ProgressSink.NONE);

        assertThat(outcome).isInstanceOfSatisfying(JobOutcome.Completed.class,
                done -> assertThat(done.result())
                        .containsEntry("supersededGenerationId", PARENT.toString()));
    }

    /**
     * The row went between the request and the worker — an account deleted, a
     * session swept. Absolute rule 3 is why this read is scoped and why gone
     * and somebody-else's are one answer.
     */
    @Test
    void anEditOfAgenerationThatIsNoLongerThereFails() {
        when(records.findById(any(), any())).thenReturn(Optional.empty());

        JobOutcome outcome = handler.handle(editJob(), ProgressSink.NONE);

        assertThat(outcome).isInstanceOf(JobOutcome.Failed.class);
        verify(reruns, never()).rerun(any(), any(), any(), any());
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private Generation firstSaved() {
        var saved = ArgumentCaptor.forClass(Generation.class);
        verify(records, org.mockito.Mockito.atLeastOnce()).save(any(), saved.capture());
        return saved.getAllValues().get(0);
    }

    private static Job editJob() {
        var payload = new SelectionEditPayload(PARENT,
                new GenerationDirectives(List.of(), List.of(EXCLUDED)));
        return new Job(JobType.GENERATION, USER, payload.toMap(), Instant.now());
    }

    private static Generation parentGeneration() {
        var options = new LinkedHashMap<String, Object>();
        options.put("templateId", "classic");
        options.put("maxPages", 1);
        var record = new Generation(USER, UUID.randomUUID(), options, snapshot(),
                new EngineVersion(EngineVersion.PIPELINE, "default", "classic:v4",
                        Map.of("job_analysis", "v1")));
        record.setPageCount(1);
        return spyId(record);
    }

    /**
     * The id a {@link Generation} mints for itself is random, and the payload
     * has to name the same one. Set through the only door there is.
     */
    private static Generation spyId(Generation record) {
        try {
            var field = Generation.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(record, PARENT);
        } catch (ReflectiveOperationException impossible) {
            throw new IllegalStateException(impossible);
        }
        return record;
    }

    private static StoredSelection snapshot() {
        return StoredSelection.of(
                new SelectionState(
                        List.of(new SelectionState.SelectedAtom(
                                EXCLUDED, UUID.randomUUID(), 0.8, 27.7, false)),
                        List.of(),
                        new SelectionState.BudgetBreakdown(648.0, 142.0, 506.0, 27.7)),
                "en", TemplateCustomization.CLASSIC);
    }

    private static GeneratedGeneration rerun() {
        var selection = new SelectionState(List.of(), List.of(),
                new SelectionState.BudgetBreakdown(648.0, 142.0, 506.0, 0.0));
        return new GeneratedGeneration(
                UUID.randomUUID(),
                (JobAnalysis) null,
                new GenerationOptions(1, "en", TemplateCustomization.CLASSIC),
                null,
                Map.of(),
                RewriteTally.none(),
                new GeneratedDocument("%PDF".getBytes(StandardCharsets.UTF_8), 1, selection,
                        new RenderRequest(
                                new RenderRequest.ProfileHeader("Ada", "", List.of()),
                                List.of(), TemplateCustomization.CLASSIC, Locale.ENGLISH),
                        1, 1.0, RewrittenContent.none()),
                null, null);
    }
}
