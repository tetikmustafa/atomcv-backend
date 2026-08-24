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
import com.mustafatetik.atomcv.generation.pipeline.GeneratedDocument;
import com.mustafatetik.atomcv.generation.pipeline.GenerationPipeline;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScores;
import com.mustafatetik.atomcv.generation.scoring.ScoredAtom;
import com.mustafatetik.atomcv.generation.scoring.ScoringWeights;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest;
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
        service = new JobSpecificGenerationService(
                profiles, assembler, tags, analysis, relevance, renderCosts, pipeline);

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

        var result = service.generateForJob(user(), POSTING, false, null, null);

        assertThat(result).isInstanceOf(Result.Err.class);
        assertThat(((Result.Err<GeneratedDocument>) result).error())
                .isInstanceOf(PipelineError.InsufficientProfile.class);
        verify(analysis, never()).analyse(anyString(), anyBoolean(), anyString());
    }

    /**
     * Faz A runs before measurement. A posting that cannot be read is going to
     * fail whatever the profile costs to render, and measurement is a
     * compilation — seconds of CPU for an answer already known.
     */
    @Test
    void anunreadablePostingCostsNoCompilation() {
        when(assembler.load(ref)).thenReturn(aprofileWithOneBullet());
        when(analysis.analyse(anyString(), anyBoolean(), anyString()))
                .thenReturn(Result.err(new PipelineError.UnparseableJobDescription(0, 0)));

        var result = service.generateForJob(user(), POSTING, false, null, null);

        assertThat(((Result.Err<GeneratedDocument>) result).error())
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
        when(analysis.analyse(anyString(), anyBoolean(), anyString()))
                .thenReturn(Result.ok(posting()));
        when(relevance.scoreAgainst(any(), any(), any())).thenReturn(new RelevanceScores(
                List.of(new ScoredAtom(atomId, 0.77,
                        new ScoredAtom.Components(0.5, 0.5, 0.5, 0.5))),
                ScoringWeights.DEFAULT));
        when(pipeline.run(any(), any(), any(), any(), any()))
                .thenReturn(Result.err(new PipelineError.PageLimitExceeded(3, 1)));

        service.generateForJob(user(), POSTING, false, null, null);

        var request = ArgumentCaptor.forClass(SelectionRequest.class);
        verify(pipeline).run(any(), any(), request.capture(), any(), any());
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
        when(analysis.analyse(anyString(), anyBoolean(), anyString()))
                .thenReturn(Result.err(new PipelineError.UnparseableJobDescription(0, 0)));

        service.generateForJob(user(), POSTING, true, null, null);

        verify(analysis).analyse(POSTING, true, USER.toString());
    }

    private static UserContext user() {
        return UserContext.of(USER);
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
