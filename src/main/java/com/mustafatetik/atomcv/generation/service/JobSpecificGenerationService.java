package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.compilation.CompilationException;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysisPhase;
import com.mustafatetik.atomcv.generation.pipeline.ContentRewriter;
import com.mustafatetik.atomcv.generation.pipeline.GenerationPipeline;
import com.mustafatetik.atomcv.generation.rewrite.BulletRewriteService;
import com.mustafatetik.atomcv.generation.rewrite.RewriteContext;
import com.mustafatetik.atomcv.generation.rewrite.RewritePhase;
import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScores;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScoringService;
import com.mustafatetik.atomcv.generation.selection.SelectionRequestBuilder;
import com.mustafatetik.atomcv.generation.validation.FitReport;
import com.mustafatetik.atomcv.generation.validation.SelectedSkills;
import com.mustafatetik.atomcv.jobs.queue.ProgressSink;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.repository.TagRepository;
import com.mustafatetik.atomcv.profile.service.CompletenessCalculator;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.rendering.measurement.RenderCostService;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * A CV written against one posting: Faz A through Faz F (Bolum 18-23).
 *
 * <p>Next to {@link CvGenerationService} rather than inside it. The two share
 * everything from selection onwards and differ in exactly two places — there
 * is an analysis, and the scores come from Faz B instead of from the profile's
 * own dates. Folding that into one method with a nullable posting would have
 * put four branches through the part that is identical.
 *
 * <p><strong>The order of the gates is the design.</strong> The profile
 * preflight is free and runs first, so an empty profile never costs an LLM
 * call; Faz A costs money and runs before measurement, so a posting that
 * cannot be read never costs a compilation. That is design principle 5, and
 * reversing any pair of them still works — it just charges the user for
 * something that was always going to fail.
 *
 * <p>Nothing is persisted yet. {@code generations} rows, the queue and
 * {@code POST /generations} arrive together in Adim 2.6; this returns the
 * document the same way general mode does.
 */
@Service
public class JobSpecificGenerationService {

    private static final Logger log = LoggerFactory.getLogger(JobSpecificGenerationService.class);

    private final ProfileResolver profiles;
    private final ProfileAssembler assembler;
    private final TagRepository tags;
    private final JobAnalysisPhase analysis;
    private final RelevanceScoringService relevance;
    private final RenderCostService renderCosts;
    private final RewritePhase rewrites;
    private final GenerationPipeline pipeline;

    JobSpecificGenerationService(
            ProfileResolver profiles,
            ProfileAssembler assembler,
            TagRepository tags,
            JobAnalysisPhase analysis,
            RelevanceScoringService relevance,
            RenderCostService renderCosts,
            RewritePhase rewrites,
            GenerationPipeline pipeline) {

        this.profiles = profiles;
        this.assembler = assembler;
        this.tags = tags;
        this.analysis = analysis;
        this.relevance = relevance;
        this.renderCosts = renderCosts;
        this.rewrites = rewrites;
        this.pipeline = pipeline;
    }

    /**
     * @param jobDescription        the pasted posting
     * @param preflightAcknowledged the user chose {@code continue_anyway}
     *                              after Bolum 18.1 refused (EK D.6.1)
     * @param maxPages              null to take the profile's own default
     * @param language              null to let the profile decide, which for
     *                              {@code auto} means following the posting
     */
    public Result<GeneratedGeneration> generateForJob(
            UserContext user,
            String jobDescription,
            boolean preflightAcknowledged,
            Integer maxPages,
            String language,
            ProgressSink progress) {

        var owned = profiles.owned(user);
        Profile head = owned.profile();
        ProfileRef profile = owned.ref();

        ProfileTree tree = assembler.load(profile);
        Result<Void> preflight = ProfilePreflight.check(head, tree);
        if (preflight.isErr()) {
            return preflight.map(ignored -> null);
        }

        // Faz A. The bucket key is the user id, so an A/B experiment keeps one
        // person on one prompt version across their generations (Bolum 53.3).
        progress.report(GenerationPhase.ANALYSING.at(10));
        String bucketKey = user.userId().toString();
        Result<JobAnalysis> analysed =
                analysis.analyse(jobDescription, preflightAcknowledged, bucketKey);
        if (analysed instanceof Result.Err<JobAnalysis> refused) {
            return Result.err(refused.error());
        }
        JobAnalysis posting = analysed.orElseThrow();

        GenerationOptions options = GenerationOptions.forPosting(head, tree, posting.jdLanguage())
                .withMaxPages(maxPages)
                .withLanguage(language);

        if (posting.jdLanguage() != null && !posting.jdLanguage().isBlank()
                && !posting.jdLanguage().strip().equals(options.language())) {
            // F-013. Not an error and not a refusal: the CV is written, in one
            // language, and the response says which one so the screen can too.
            log.info("Posting is in {} but the CV is written in {}; "
                    + "the profile has no wording for every atom in the posting's language",
                    posting.jdLanguage().strip(), options.language());
        }

        CapacityModel capacity = TemplateRegistry.capacityOf(options.customization())
                .orElseThrow(() -> new IllegalStateException(
                        "This customization has never been calibrated; measure it first"));

        progress.report(GenerationPhase.MEASURING.at(30));

        // One compilation for everything that has no cost yet, before
        // selection asks for numbers (Bolum 26.2).
        try {
            if (renderCosts.measureMissing(profile, options.customization()) > 0) {
                tree = assembler.load(profile);
            }
        } catch (CompilationException failed) {
            return Result.err(
                    new PipelineError.CompilationFailed(failed.kind(), failed.log()));
        }

        progress.report(GenerationPhase.SCORING.at(50));

        // Faz B. The fifth query of the generation, and the only one general
        // mode does not make: tags are a scoring input, not part of the tree
        // that gets rendered (Bolum 52.2).
        RelevanceScores scores =
                relevance.scoreAgainst(tree, tags.labelsByAtom(profile), posting);

        var built = SelectionRequestBuilder.build(tree, options.customization(), capacity,
                options.maxPages(), options.language(),
                head.getPreferences().writingStyle().tone(), scores);

        if (built.request().sections().isEmpty()) {
            // Everything was inactive, or nothing had a wording. Either way
            // there is no CV to make, and saying so beats an empty page.
            return Result.err(new PipelineError.InsufficientProfile(
                    CompletenessCalculator.of(head, tree), List.of("atoms")));
        }
        if (built.estimatedAtoms() > 0) {
            log.info("Selecting with {} estimated costs and {} atoms with no wording",
                    built.estimatedAtoms(), built.withoutWording());
        }

        // Faz D and Faz F both read the tree the selection was made from, so
        // the reference has to survive the lambdas — measurement may have
        // reloaded it above.
        ProfileTree rendered = tree;

        // Faz D, inside the compile loop because that is where a selection
        // exists. It reports itself when it runs: general mode never gets
        // here, and even here there may be nothing worth rewriting.
        var context = RewriteContext.of(posting, options.language(),
                head.getPreferences().writingStyle().tone(), bucketKey);
        var rewritten = new AtomicReference<>(RewrittenContent.none());
        var announced = new AtomicBoolean();
        ContentRewriter rewriter = (state, carried) -> {
            // Once, however many times the compile loop goes round. A bar
            // that walked back from seventy to sixty would be reporting a
            // retry the user was never told about.
            boolean first = announced.compareAndSet(false, true);
            if (first) {
                progress.report(GenerationPhase.REWRITING.at(60));
            }
            RewrittenContent done = rewrites.rewrite(rendered, state, context, carried);
            rewritten.set(done);
            if (first) {
                progress.report(GenerationPhase.RENDERING.at(70));
            }
            return done;
        };

        return pipeline.run(head, tree, built.request(), rewriter,
                        options.customization(), options.locale())
                .map(document -> new GeneratedGeneration(
                        profile.id(), posting, options, scores.weights(),
                        promptVersions(bucketKey, rewritten.get()),
                        document,
                        // Bolum 23.3, and it is measured on what the page
                        // prints rather than on what Faz B ranked: selection
                        // drops most of the profile for budget, and a report
                        // built from the ranking would credit the user for a
                        // skill that never made it onto the document.
                        FitReport.of(posting,
                                SelectedSkills.onThePage(rendered, document.selection()))));
    }

    /**
     * The versions that actually ran (Bolum 53.3). Faz D's is recorded only
     * when Faz D changed something: a record naming a rewrite prompt for a
     * generation that printed the profile verbatim would send anybody reading
     * it back to the wrong prompt.
     */
    private Map<String, String> promptVersions(String bucketKey, RewrittenContent rewritten) {
        var versions = new LinkedHashMap<String, String>();
        versions.put(JobAnalysisPhase.PROMPT_ID, analysis.promptVersionFor(bucketKey));
        if (!rewritten.isEmpty()) {
            versions.put(BulletRewriteService.PROMPT_ID, rewrites.promptVersionFor(bucketKey));
        }
        return versions;
    }
}
