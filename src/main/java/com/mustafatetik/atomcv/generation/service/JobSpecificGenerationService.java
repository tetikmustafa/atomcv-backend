package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.compilation.CompilationException;
import com.mustafatetik.atomcv.generation.coverletter.CoverLetterStyle;
import com.mustafatetik.atomcv.generation.coverletter.CoverLetterWriter;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysisPhase;
import com.mustafatetik.atomcv.generation.pipeline.ContentRewriter;
import com.mustafatetik.atomcv.generation.pipeline.GenerationPipeline;
import com.mustafatetik.atomcv.generation.rewrite.AboutSynthesisService;
import com.mustafatetik.atomcv.generation.rewrite.BulletRewriteService;
import com.mustafatetik.atomcv.generation.rewrite.RewriteContext;
import com.mustafatetik.atomcv.generation.rewrite.RewriteOutcome;
import com.mustafatetik.atomcv.generation.rewrite.RewritePhase;
import com.mustafatetik.atomcv.generation.rewrite.RewriteTally;
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
import com.mustafatetik.atomcv.rendering.measurement.RenderCostService;
import com.mustafatetik.atomcv.rendering.measurement.Capacities;
import com.mustafatetik.atomcv.rendering.measurement.TemplateMeasurements;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
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

    private final ProfileAssembler assembler;
    private final Capacities capacities;
    private final TemplateMeasurements measurements;
    private final TagRepository tags;
    private final JobAnalysisPhase analysis;
    private final RelevanceScoringService relevance;
    private final RenderCostService renderCosts;
    private final RewritePhase rewrites;
    private final CoverLetterWriter letters;
    private final GenerationPipeline pipeline;

    JobSpecificGenerationService(
            ProfileAssembler assembler,
            TagRepository tags,
            JobAnalysisPhase analysis,
            RelevanceScoringService relevance,
            RenderCostService renderCosts,
            RewritePhase rewrites,
            CoverLetterWriter letters,
            GenerationPipeline pipeline,
            Capacities capacities, TemplateMeasurements measurements) {
        this.capacities = capacities;
        this.measurements = measurements;

        this.assembler = assembler;
        this.tags = tags;
        this.analysis = analysis;
        this.relevance = relevance;
        this.renderCosts = renderCosts;
        this.rewrites = rewrites;
        this.letters = letters;
        this.pipeline = pipeline;
    }

    /**
     * @param jobDescription        the pasted posting
     * @param preflightAcknowledged the user chose {@code continue_anyway}
     *                              after Bolum 18.1 refused (EK D.6.1)
     * @param coverLetter           Bolum 34's letter was asked for. Off by
     *                              default and a second call when it is on;
     *                              a letter that cannot be written honestly
     *                              does not fail the CV
     * @param maxPages              null to take the profile's own default
     * @param language              null to let the profile decide, which for
     *                              {@code auto} means following the posting
     */
    public Result<GeneratedGeneration> generateForJob(
            GenerationSubject subject,
            String jobDescription,
            boolean preflightAcknowledged,
            Integer maxPages,
            String language,
            boolean coverLetter,
            ProgressSink progress,
            java.util.UUID jobId) {

        Profile head = subject.head();
        ProfileRef profile = subject.profile();

        ProfileTree tree = assembler.load(profile);
        Result<Void> preflight = ProfilePreflight.check(head, tree);
        if (preflight.isErr()) {
            return preflight.map(ignored -> null);
        }

        // Faz A. The bucket key keeps one person on one prompt version across
        // their generations (Bolum 53.3) -- their user id, or the profile id when
        // there is no account behind the request.
        progress.report(GenerationPhase.ANALYSING.at(10));
        String bucketKey = subject.bucketKey();
        Result<JobAnalysis> analysed =
                analysis.analyse(jobDescription, preflightAcknowledged, bucketKey,
                        subject.userId(), jobId);
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

        // Measured if anybody has compiled this geometry, estimated if not
        // (Bolum 33.3). Empty now means only that the template itself has no
        // measured default, which would be estimating from nothing.
        Capacities.Resolved resolved = capacities.resolve(options.customization())
                .orElseThrow(() -> new IllegalStateException(
                        "This template has never been calibrated; measure it first"));
        CapacityModel capacity = resolved.capacity();
        if (resolved.estimated()) {
            // Bolum 33.3's third step, asked for at the moment somebody
            // actually falls back to a guess. This run still produces a CV --
            // against the estimate, spending a little less of the page -- and
            // the next one at these settings is exact.
            measurements.request(options.customization());
        }

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
        var context = RewriteContext.of(posting, head.getSelfDescription(),
                options.language(), head.getPreferences().writingStyle().tone(), bucketKey,
                subject.userId(), jobId);
        var rewritten = new AtomicReference<>(RewrittenContent.none());
        // Accumulated across the compile loop rather than overwritten. A
        // document that came out too long has already paid for the pass before
        // it, and the trace is a record of what was spent.
        var tally = new AtomicReference<>(RewriteTally.none());
        var announced = new AtomicBoolean();
        ContentRewriter rewriter = (state, carried) -> {
            // Once, however many times the compile loop goes round. A bar
            // that walked back from seventy to sixty would be reporting a
            // retry the user was never told about.
            boolean first = announced.compareAndSet(false, true);
            if (first) {
                progress.report(GenerationPhase.REWRITING.at(60));
            }
            RewriteOutcome done = rewrites.rewrite(rendered, state, context, carried);
            rewritten.set(done.content());
            tally.updateAndGet(sofar -> sofar.plus(done.tally()));
            if (first) {
                progress.report(GenerationPhase.RENDERING.at(70));
            }
            return done.content();
        };

        return pipeline.run(head, tree,
                        built.request().withBudgetFactor(resolved.budgetFactor()), rewriter,
                        options.customization(), options.locale())
                .map(document -> new GeneratedGeneration(
                        profile.id(), posting, options, scores.weights(),
                        promptVersions(bucketKey, tally.get()),
                        tally.get(),
                        document,
                        // Bolum 23.3, and it is measured on what the page
                        // prints rather than on what Faz B ranked: selection
                        // drops most of the profile for budget, and a report
                        // built from the ranking would credit the user for a
                        // skill that never made it onto the document.
                        FitReport.of(posting,
                                SelectedSkills.onThePage(rendered, document.selection())),
                        null))
                // Bolum 34, and it comes last on purpose: the CV is what the
                // person asked for, and a letter that could not be written
                // honestly must not take the document down with it.
                .map(made -> coverLetter
                        ? made.withCoverLetter(letters.writeQuietly(
                                head, rendered, made.document().selection(), posting,
                                "", CoverLetterStyle.DEFAULT, bucketKey, subject.userId(),
                                jobId))
                        : made);
    }

    /**
     * The versions that actually ran (Bolum 53.3), and it is now read off the
     * calls rather than off the result.
     *
     * <p><strong>Duzeltme.</strong> This used to key off "Faz D changed
     * something", which conflated three different runs. A generation whose only
     * accepted answer was the About paragraph recorded {@code bullet_rewrite}
     * as having run — it had not, or it had and every answer was refused, and
     * the record could not say which. And a pass where both prompts ran and
     * both were refused recorded neither, so a prompt that had started
     * producing nothing but unsupported claims left no trace of having been
     * asked. A prompt version belongs in this record when a request went out
     * under it, whatever came back.
     */
    private Map<String, String> promptVersions(String bucketKey, RewriteTally tally) {
        var versions = new LinkedHashMap<String, String>();
        versions.put(JobAnalysisPhase.PROMPT_ID, analysis.promptVersionFor(bucketKey));
        if (tally.callsByPrompt().containsKey(BulletRewriteService.PROMPT_ID)) {
            versions.put(BulletRewriteService.PROMPT_ID, rewrites.promptVersionFor(bucketKey));
        }
        if (tally.callsByPrompt().containsKey(AboutSynthesisService.PROMPT_ID)) {
            versions.put(AboutSynthesisService.PROMPT_ID,
                    rewrites.aboutPromptVersionFor(bucketKey));
        }
        return versions;
    }
}
