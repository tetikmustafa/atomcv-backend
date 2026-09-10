package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.pipeline.ContentRewriter;
import com.mustafatetik.atomcv.generation.pipeline.GenerationPipeline;
import com.mustafatetik.atomcv.generation.rewrite.RewriteTally;
import com.mustafatetik.atomcv.generation.scoring.AtomScoreSource;
import com.mustafatetik.atomcv.generation.selection.GenerationDirectives;
import com.mustafatetik.atomcv.generation.selection.SelectionRequestBuilder;
import com.mustafatetik.atomcv.generation.validation.FitReport;
import com.mustafatetik.atomcv.generation.validation.SelectedSkills;
import com.mustafatetik.atomcv.jobs.queue.ProgressSink;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.service.CompletenessCalculator;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.rendering.measurement.Capacities;
import com.mustafatetik.atomcv.rendering.measurement.RenderCostService;
import com.mustafatetik.atomcv.rendering.measurement.TemplateMeasurements;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Faz G's edit loop, from Faz C onwards (Bolum 24.1).
 *
 * <p>The rule the whole design rests on: <strong>an edit applies to the
 * selection state, never to the rendered output.</strong> Twenty edits and the
 * page limit still holds, because every one of them goes back through the same
 * selection that made the promise in the first place.
 *
 * <p><strong>From Faz C, which is what makes it cheap.</strong> The posting was
 * read once and the profile was ranked against it once, and neither answer
 * changes when somebody switches a bullet off — so Faz A does not run, Faz B
 * does not run, and the scores come back out of the snapshot the parent row
 * already holds. Faz D does not ask a model anything either: it carries the
 * wording it wrote the first time ({@link ContentRewriter#carrying}). What is
 * left is selection, render and compile — no LLM call at all, which is why a
 * manual toggle costs nothing against the day's allowance.
 *
 * <p><strong>It runs in the world the parent generation was made in.</strong>
 * The scores, the language, the template and the page limit all come off that
 * row rather than off today's profile. An atom written since is unscored and
 * therefore unranked; it can still be asked for by name, because a directive
 * outranks a score. That follows from Bolum 24.1 rather than being chosen
 * here: re-reading the profile would mean re-scoring it, and re-scoring is Faz
 * B.
 */
@Service
public class GenerationRerunService {

    private static final Logger log = LoggerFactory.getLogger(GenerationRerunService.class);

    private final ProfileAssembler assembler;
    private final Capacities capacities;
    private final TemplateMeasurements measurements;
    private final GenerationPipeline pipeline;

    GenerationRerunService(ProfileAssembler assembler, GenerationPipeline pipeline,
            Capacities capacities, TemplateMeasurements measurements) {
        this.capacities = capacities;
        this.measurements = measurements;
        this.assembler = assembler;
        this.pipeline = pipeline;
    }

    /**
     * @param parent     the generation being edited. It is read, never
     *                   written: the caller marks it superseded once the new
     *                   document exists, because a row replaced by a run that
     *                   then failed would leave the person with no current CV
     * @param directives everything asked for by hand so far, the parent's own
     *                   included — an edit is the sum of the edits before it,
     *                   not the last one
     */
    public Result<GeneratedGeneration> rerun(
            GenerationSubject subject,
            Generation parent,
            GenerationDirectives directives,
            ProgressSink progress) {

        Profile head = subject.head();
        ProfileRef profile = subject.profile();
        StoredSelection snapshot = parent.getSelectionState();

        GenerationOptions options = new GenerationOptions(
                maxPagesOf(parent), snapshot.language(), snapshot.customization());

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

        // No measurement pass. Every atom the snapshot knows about was costed
        // when the parent was made, and the cost lives on the variant rather
        // than on the row -- measuring again would buy one compilation to
        // learn what is already in the table (Bolum 26.2).
        ProfileTree tree = assembler.load(profile);

        // One phase, because from here it is one piece of work: Faz C, the
        // render and the compile loop. RENDERING is the phase that carries
        // "C" (Bolum 30.6), and an edit never reaches the three before it.
        progress.report(GenerationPhase.RENDERING.at(40));

        var built = SelectionRequestBuilder.build(tree, options.customization(), capacity,
                options.maxPages(), options.language(),
                head.getPreferences().writingStyle().tone(),
                AtomScoreSource.remembered(snapshot.scoresByCandidate()),
                measuredHeaderOf(head, options));

        if (built.request().sections().isEmpty()) {
            // The profile was emptied out between the generation and the edit.
            // There is no CV to re-make, and the parent row still stands.
            return Result.err(new PipelineError.InsufficientProfile(
                    CompletenessCalculator.of(head, tree), List.of("atoms")));
        }

        ProfileTree rendered = tree;
        return pipeline.run(head, tree,
                        built.request().withBudgetFactor(resolved.budgetFactor())
                                .withDirectives(directives),
                        ContentRewriter.carrying(parent.getRewrittenContent()),
                        options.customization(), options.locale())
                .map(document -> new GeneratedGeneration(
                        profile.id(),
                        parent.getJdAnalysis(),
                        options,
                        null,
                        Map.of(),
                        // Faz D asked nothing, so there is nothing to tally.
                        // Zero calls is a fact about this run.
                        RewriteTally.none(),
                        document,
                        // Recomputed rather than copied: the edit changed what
                        // is on the page, and a report carried over from the
                        // parent would credit the user for a skill they just
                        // removed (Bolum 23.3).
                        parent.getJdAnalysis() == null
                                ? null
                                : FitReport.of(parent.getJdAnalysis(),
                                        SelectedSkills.onThePage(rendered, document.selection())),
                        // Bolum 34's letter is not re-derived. It was written
                        // about a selection this edit has just changed, and
                        // rewriting it is a model call the toggle promised not
                        // to make -- the letter is carried, and Bolum 34.6's
                        // own button is how it is refreshed.
                        parent.getCoverLetter()));
    }

    private static int maxPagesOf(Generation parent) {
        Object stored = parent.getOptions().get("maxPages");
        if (stored instanceof Number pages) {
            return pages.intValue();
        }
        // A row whose options predate the field, or one written by a release
        // that spelled it differently. One page is what the product promises
        // by default, and a re-run that quietly granted two would break the
        // promise the parent was made under.
        log.warn("Generation {} carries no maxPages; the edit re-runs against one page",
                parent.getId());
        return 1;
    }

    /**
     * What this profile's header measured at these settings, or null.
     *
     * <p>Null is the ordinary state exactly once: the first generation after a
     * person changes their name, their headline or a contact line. The
     * measuring pass above fills it in before selection asks, so the fallback
     * is for the run where the compiler could not be reached at all.
     */
    private static Double measuredHeaderOf(Profile head, GenerationOptions options) {
        return head.getHeaderCosts().get(RenderCostService.headerKeyOf(
                options.customization().costKey(),
                java.util.Locale.forLanguageTag(options.language())));
    }

}
