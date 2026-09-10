package com.mustafatetik.atomcv.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysisPhase;
import com.mustafatetik.atomcv.generation.pipeline.ContentRewriter;
import com.mustafatetik.atomcv.generation.pipeline.GeneratedDocument;
import com.mustafatetik.atomcv.generation.pipeline.GenerationPipeline;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.generation.coverletter.CoverLetterWriter;
import com.mustafatetik.atomcv.generation.rewrite.AboutSynthesisService;
import com.mustafatetik.atomcv.generation.rewrite.BulletRewriteService;
import com.mustafatetik.atomcv.generation.rewrite.RewriteContext;
import com.mustafatetik.atomcv.generation.rewrite.RewriteIssue;
import com.mustafatetik.atomcv.generation.rewrite.RewriteTally;
import com.mustafatetik.atomcv.generation.rewrite.RewriteOutcome;
import com.mustafatetik.atomcv.generation.rewrite.RewritePhase;
import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScores;
import com.mustafatetik.atomcv.generation.scoring.ScoredAtom;
import com.mustafatetik.atomcv.generation.scoring.ScoringWeights;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest;
import com.mustafatetik.atomcv.jobs.queue.ProgressSink;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.repository.TagRepository;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.rendering.measurement.RenderCostService;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.UnreadablePostingReason;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The order of the gates, which is the whole design of this service
 * (design principle 5).
 *
 * <p>Each gate is cheaper than the one after it, and each test here is about
 * something the user is <em>not</em> charged for: an empty profile costs no
 * LLM call, an unreadable posting costs no compilation. Getting the order
 * wrong breaks no build and produces no wrong output — it only spends money.
 */
class JobSpecificGenerationServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final String POSTING = "We are looking for a senior backend engineer.";

    private ProfileResolver profiles;
    private ProfileAssembler assembler;
    private TagRepository tags;
    private JobAnalysisPhase analysis;
    private com.mustafatetik.atomcv.generation.scoring.RelevanceScoringService relevance;
    private RenderCostService renderCosts;
    private RewritePhase rewrites;
    private CoverLetterWriter letters;
    private GenerationPipeline pipeline;
    private JobSpecificGenerationService service;

    private Profile head;
    private ProfileRef ref;

    @BeforeEach
    void wireTheMocks() {
        profiles = mock(ProfileResolver.class);
        assembler = mock(ProfileAssembler.class);
        tags = mock(TagRepository.class);
        analysis = mock(JobAnalysisPhase.class);
        relevance = mock(com.mustafatetik.atomcv.generation.scoring.RelevanceScoringService.class);
        renderCosts = mock(RenderCostService.class);
        pipeline = mock(GenerationPipeline.class);
        rewrites = mock(RewritePhase.class);
        when(rewrites.rewrite(any(), any(), any(), any()))
                .thenReturn(RewriteOutcome.of(RewrittenContent.none()));
        letters = mock(CoverLetterWriter.class);
        // Answers out of the registry, which is what the built-in templates
        // did before Capacities existed: these cases are about the phases, not
        // about where a capacity is looked up.
        var capacities = mock(com.mustafatetik.atomcv.rendering.measurement.Capacities.class);
        when(capacities.resolve(any())).thenAnswer(call ->
                com.mustafatetik.atomcv.rendering.template.TemplateRegistry
                        .capacityOf(call.<com.mustafatetik.atomcv.rendering.template
                                .TemplateCustomization>getArgument(0))
                        .map(capacity -> new com.mustafatetik.atomcv.rendering.measurement
                                .Capacities.Resolved(capacity, false)));
        service = new JobSpecificGenerationService(assembler, tags, analysis,
                relevance, renderCosts, rewrites, letters, pipeline, capacities,
                mock(com.mustafatetik.atomcv.rendering.measurement.TemplateMeasurements.class));

        head = new Profile(USER);
        ref = ProfileRef.persistent(UserContext.of(USER), UUID.randomUUID(), USER);
        when(profiles.owned(any())).thenReturn(new ProfileResolver.OwnedProfile(head, ref));
        when(tags.labelsByAtom(any())).thenReturn(Map.of());
    }

    /**
     * The free gate runs first. A profile with nothing in it was never going
     * to produce a CV, and asking a model to read the posting first would
     * charge the user for finding that out.
     */
    @Test
    void anemptyProfileCostsNoLlmCall() {
        when(assembler.load(ref)).thenReturn(new ProfileTree(ref.id(), List.of()));

        var result = service.generateForJob(subject(), POSTING, false, null, null, false, ProgressSink.NONE, null);

        assertThat(result).isInstanceOf(Result.Err.class);
        assertThat(((Result.Err<GeneratedGeneration>) result).error())
                .isInstanceOf(PipelineError.InsufficientProfile.class);
        verify(analysis, never()).analyse(anyString(), anyBoolean(), anyString(), any(), any());
    }

    /**
     * Faz A runs before measurement. A posting that cannot be read is going to
     * fail whatever the profile costs to render, and measurement is a
     * compilation — seconds of CPU for an answer already known.
     */
    @Test
    void anunreadablePostingCostsNoCompilation() {
        when(assembler.load(ref)).thenReturn(aprofileWithOneBullet());
        when(analysis.analyse(anyString(), anyBoolean(), anyString(), any(), any()))
                .thenReturn(Result.err(new PipelineError.UnparseableJobDescription(
                        0, 0, UnreadablePostingReason.NOT_JOB_LIKE)));

        var result = service.generateForJob(subject(), POSTING, false, null, null, false, ProgressSink.NONE, null);

        assertThat(((Result.Err<GeneratedGeneration>) result).error())
                .isInstanceOf(PipelineError.UnparseableJobDescription.class);
        verify(renderCosts, never()).measureMissing(any(), any());
        verify(relevance, never()).scoreAgainst(any(), any(), any());
    }

    /**
     * The happy path, as far as the pipeline's door. What comes out the other
     * side is GenerationPipeline's own test; what matters here is that the
     * numbers selection works on are Faz B's and not the general-mode
     * scorer's (Bolum 19.4).
     */
    @Test
    void theselectionRequestCarriesFazBsScores() {
        ProfileTree tree = aprofileWithOneBullet();
        UUID atomId = tree.sections().get(0).entries().get(0).atoms().get(0).atom().getId();
        when(assembler.load(ref)).thenReturn(tree);
        when(analysis.analyse(anyString(), anyBoolean(), anyString(), any(), any()))
                .thenReturn(Result.ok(posting()));
        when(relevance.scoreAgainst(any(), any(), any())).thenReturn(new RelevanceScores(
                List.of(new ScoredAtom(atomId, 0.77, 0.5,
                        new ScoredAtom.Components(0.5, 0.5, 0.5, 0.5))),
                ScoringWeights.DEFAULT));
        when(pipeline.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(Result.err(new PipelineError.PageLimitExceeded(3, 1)));

        service.generateForJob(subject(), POSTING, false, null, null, false, ProgressSink.NONE, null);

        var request = ArgumentCaptor.forClass(SelectionRequest.class);
        verify(pipeline).run(any(), any(), request.capture(), any(), any(), any());
        assertThat(request.getValue().sections().get(0).entries().get(0).atoms())
                .singleElement()
                .satisfies(atom -> assertThat(atom.score()).isEqualTo(0.77));
    }

    /**
     * The bucket key is the user id, so an A/B experiment keeps one person on
     * one prompt version across their generations (Bolum 53.3).
     */
    @Test
    void thepromptBucketIsTheUser() {
        when(assembler.load(ref)).thenReturn(aprofileWithOneBullet());
        when(analysis.analyse(anyString(), anyBoolean(), anyString(), any(), any()))
                .thenReturn(Result.err(new PipelineError.UnparseableJobDescription(
                        0, 0, UnreadablePostingReason.NOT_JOB_LIKE)));

        service.generateForJob(subject(), POSTING, true, null, null, false, ProgressSink.NONE, null);

        verify(analysis).analyse(POSTING, true, USER.toString(), USER, null);
    }

    /**
     * Bolum 27.5's {@code llm_invocations.job_id}. The column existed and
     * nothing wrote it, so tying a billed call to the work that caused it meant
     * matching timestamps to the millisecond — which is how a four-page CV was
     * found to have been paid for twice with one job row to show for it. Faz A
     * is the first call a generation makes and the cheapest place to prove the
     * id travels at all.
     */
    @Test
    void thejobIsCarriedIntoTheCallsItPaysFor() {
        var jobId = java.util.UUID.randomUUID();
        when(assembler.load(ref)).thenReturn(aprofileWithOneBullet());
        when(analysis.analyse(anyString(), anyBoolean(), anyString(), any(), any()))
                .thenReturn(Result.err(new PipelineError.UnparseableJobDescription(
                        0, 0, UnreadablePostingReason.NOT_JOB_LIKE)));

        service.generateForJob(subject(), POSTING, true, null, null, false,
                ProgressSink.NONE, jobId);

        verify(analysis).analyse(POSTING, true, USER.toString(), USER, jobId);
    }

    private static UserContext user() {
        return UserContext.of(USER);
    }

    /**
     * The subject the pipeline now takes. It carries the profile already
     * resolved, so the resolver is no longer a dependency of the service --
     * which is why this test builds one rather than stubbing a lookup.
     */
    private GenerationSubject subject() {
        return GenerationSubject.account(new ProfileResolver.OwnedProfile(head, ref), USER);
    }

    /**
     * <strong>Faz D is wired, and it is wired to this posting.</strong> The
     * pipeline is handed a rewriter rather than a phase, so the thing worth
     * asserting is what that rewriter does when the pipeline calls it: Bolum
     * 21.6's guard vocabulary is the posting's skills, and getting the wrong
     * posting in here would be a validator checking against somebody else's.
     */
    @Test
    void thepipelineIsHandedAFazDThatKnowsThisPosting() {
        ProfileTree tree = aprofileWithOneBullet();
        when(assembler.load(ref)).thenReturn(tree);
        when(analysis.analyse(anyString(), anyBoolean(), anyString(), any(), any()))
                .thenReturn(Result.ok(posting()));
        when(relevance.scoreAgainst(any(), any(), any()))
                .thenReturn(new RelevanceScores(List.of(), ScoringWeights.DEFAULT));
        when(pipeline.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(Result.err(new PipelineError.PageLimitExceeded(3, 1)));

        service.generateForJob(subject(), POSTING, false, null, null, false, ProgressSink.NONE, null);

        var rewriter = ArgumentCaptor.forClass(ContentRewriter.class);
        verify(pipeline).run(any(), any(), any(), rewriter.capture(), any(), any());
        var selection = new SelectionState(List.of(), List.of(),
                new SelectionState.BudgetBreakdown(600, 100, 500, 300));
        rewriter.getValue().rewrite(selection, RewrittenContent.none());

        var context = ArgumentCaptor.forClass(RewriteContext.class);
        verify(rewrites).rewrite(any(), any(), context.capture(), any());
        assertThat(context.getValue().postingSkills()).containsExactly("go");
        assertThat(context.getValue().language()).isEqualTo("en");
        assertThat(context.getValue().bucketKey()).isEqualTo(USER.toString());
    }

    /**
     * <strong>Duzeltme (Bolum 53.3).</strong> A prompt version belongs in the
     * record when a request went out under it, and the record used to be keyed
     * off whether Faz D had <em>changed</em> anything: a generation whose only
     * accepted answer was the About paragraph recorded {@code bullet_rewrite}
     * as having run, and a pass where both prompts ran and every answer was
     * refused recorded neither. Both readings send whoever is chasing a
     * regression to the wrong prompt.
     */
    @Test
    void onlyThePromptsThatActuallyMadeACallAreRecorded() {
        aGenerationThatRunsFazD(new RewriteOutcome(RewrittenContent.none(),
                new RewriteTally(Map.of(AboutSynthesisService.PROMPT_ID, 1), Map.of(), 0)));

        var made = service.generateForJob(
                subject(), POSTING, false, null, null, false, ProgressSink.NONE, null);

        assertThat(made.orElseThrow().promptVersions())
                .containsKey(AboutSynthesisService.PROMPT_ID)
                .doesNotContainKey(BulletRewriteService.PROMPT_ID);
    }

    /**
     * And a pass that called and was refused every time is recorded as having
     * run. It changed nothing, which is exactly the state that used to leave no
     * trace of the prompt that produced it.
     */
    @Test
    void apromptThatRanAndWasRefusedIsStillRecordedAsHavingRun() {
        aGenerationThatRunsFazD(new RewriteOutcome(RewrittenContent.none(),
                new RewriteTally(Map.of(BulletRewriteService.PROMPT_ID, 2),
                        Map.of(RewriteIssue.UNSUPPORTED_CLAIM, 2), 0)));

        var made = service.generateForJob(
                subject(), POSTING, false, null, null, false, ProgressSink.NONE, null);

        assertThat(made.orElseThrow().promptVersions())
                .containsKey(BulletRewriteService.PROMPT_ID);
        assertThat(made.orElseThrow().rewriteTally().refusals())
                .containsEntry(RewriteIssue.UNSUPPORTED_CLAIM, 2);
    }

    /** Everything up to the pipeline, with the pipeline actually calling Faz D. */
    private void aGenerationThatRunsFazD(RewriteOutcome outcome) {
        when(assembler.load(ref)).thenReturn(aprofileWithOneBullet());
        when(analysis.analyse(anyString(), anyBoolean(), anyString(), any(), any()))
                .thenReturn(Result.ok(posting()));
        when(relevance.scoreAgainst(any(), any(), any()))
                .thenReturn(new RelevanceScores(List.of(), ScoringWeights.DEFAULT));
        when(rewrites.rewrite(any(), any(), any(), any())).thenReturn(outcome);
        when(rewrites.promptVersionFor(any())).thenReturn("v1");
        when(rewrites.aboutPromptVersionFor(any())).thenReturn("v1");
        when(pipeline.run(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            ContentRewriter rewriter = call.getArgument(3);
            rewriter.rewrite(new SelectionState(List.of(), List.of(),
                    new SelectionState.BudgetBreakdown(600, 100, 500, 300)),
                    RewrittenContent.none());
            return Result.ok(aDocument());
        });
    }

    /**
     * <strong>Design principle 5, as a default.</strong> A covering letter is
     * a second LLM call, and most people asking for a CV want a CV. Nobody
     * pays for one they did not ask for.
     */
    @Test
    void nocoverLetterIsWrittenUnlessItWasAskedFor() {
        aGenerationThatReachesThePipeline();

        service.generateForJob(subject(), POSTING, false, null, null, false, ProgressSink.NONE, null);

        verify(letters, never()).writeQuietly(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * And when it was asked for, a letter that could not be written honestly
     * does not take the CV with it — {@code writeQuietly} answers with nothing
     * and the document is unaffected.
     */
    @Test
    void acoverLetterIsWrittenWhenItWasAskedForAndNeverFailsTheCv() {
        aGenerationThatReachesThePipeline();
        when(letters.writeQuietly(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        service.generateForJob(subject(), POSTING, false, null, null, true, ProgressSink.NONE, null);

        verify(letters).writeQuietly(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    /** Everything up to the pipeline's door, so the letter is the only variable. */
    private void aGenerationThatReachesThePipeline() {
        when(assembler.load(ref)).thenReturn(aprofileWithOneBullet());
        when(analysis.analyse(anyString(), anyBoolean(), anyString(), any(), any()))
                .thenReturn(Result.ok(posting()));
        when(relevance.scoreAgainst(any(), any(), any()))
                .thenReturn(new RelevanceScores(List.of(), ScoringWeights.DEFAULT));
        when(pipeline.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(Result.ok(aDocument()));
    }

    /** The thinnest thing the pipeline can answer with: one page, nothing on it. */
    private static GeneratedDocument aDocument() {
        var selection = new SelectionState(List.of(), List.of(),
                new SelectionState.BudgetBreakdown(648.0, 142.0, 506.0, 0.0));
        var rendered = new RenderRequest(
                new RenderRequest.ProfileHeader("Ada Lovelace", null, List.of()),
                List.of(), TemplateCustomization.CLASSIC, java.util.Locale.ENGLISH);
        return new GeneratedDocument(new byte[] {1}, 1, selection, rendered, 1, 1.0,
                RewrittenContent.none());
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

    private ProfileTree aprofileWithOneBullet() {
        UUID profileId = ref.id();
        List<Section> sections = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        List<Atom> atoms = new ArrayList<>();
        List<AtomVariant> variants = new ArrayList<>();

        var section = new Section(profileId, SectionKind.EXPERIENCE, "Experience", (short) 0);
        sections.add(section);
        var entry = new Entry(profileId, section.getId(), "Engineer", (short) 0);
        entry.setStartDate(LocalDate.of(2020, 1, 1));
        entries.add(entry);
        var atom = new Atom(
                profileId, section.getId(), entry.getId(), AtomKind.BULLET, (short) 0);
        atoms.add(atom);
        var variant = new AtomVariant(profileId, atom.getId(), "en",
                RichContent.plain("Built payment systems in Go"));
        variant.setPrimary(true);
        variants.add(variant);

        return ProfileAssembler.assemble(profileId, sections, entries, atoms, variants);
    }
}
