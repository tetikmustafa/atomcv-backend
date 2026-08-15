package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.compilation.CompilationException;
import com.mustafatetik.atomcv.generation.pipeline.GeneratedDocument;
import com.mustafatetik.atomcv.generation.pipeline.GenerationPipeline;
import com.mustafatetik.atomcv.generation.pipeline.PipelineError;
import com.mustafatetik.atomcv.generation.pipeline.Result;
import com.mustafatetik.atomcv.generation.selection.SelectionRequestBuilder;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.service.CompletenessCalculator;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.rendering.measurement.RenderCostService;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * A CV with no job description to write it against (XI-A.3 Adim 1.8).
 *
 * <p>Faz A and Faz B are skipped — there is nothing to analyse and nothing to
 * be relevant to — and the rest of the pipeline is unchanged. That is what
 * separating scoring from selection buys: general mode is a different score
 * function and nothing else (Bolum 19.4).
 *
 * <p>The order of the steps is the point. Measurement happens before the tree
 * is loaded, so selection reads costs that exist; the profile is checked before
 * measurement, so an empty profile costs no compilation at all (design
 * principle 5).
 */
@Service
public class CvGenerationService {

    private static final Logger log = LoggerFactory.getLogger(CvGenerationService.class);

    private final ProfileResolver profiles;
    private final ProfileAssembler assembler;
    private final RenderCostService renderCosts;
    private final GenerationPipeline pipeline;
    private final Clock clock;

    CvGenerationService(
            ProfileResolver profiles,
            ProfileAssembler assembler,
            RenderCostService renderCosts,
            GenerationPipeline pipeline,
            Clock clock) {

        this.profiles = profiles;
        this.assembler = assembler;
        this.renderCosts = renderCosts;
        this.pipeline = pipeline;
        this.clock = clock;
    }

    /**
     * @param maxPages null to take the profile's own default
     * @param language null to take the profile's own default
     */
    public Result<GeneratedDocument> generateGeneralCv(
            UserContext user, Integer maxPages, String language) {

        var owned = profiles.owned(user);
        Profile head = owned.profile();
        ProfileRef profile = owned.ref();
        GenerationOptions options = GenerationOptions.defaultsOf(head)
                .withMaxPages(maxPages)
                .withLanguage(language);

        CapacityModel capacity = TemplateRegistry.capacityOf(options.customization())
                .orElseThrow(() -> new IllegalStateException(
                        "This customization has never been calibrated; measure it first"));

        ProfileTree tree = assembler.load(profile);
        Result<Void> preflight = checkEnoughToWorkWith(head, tree);
        if (preflight.isErr()) {
            return preflight.map(ignored -> null);
        }

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

        var built = SelectionRequestBuilder.build(tree, options.customization(), capacity,
                options.maxPages(), options.language(), LocalDate.now(clock));

        if (built.request().sections().isEmpty()) {
            // Everything was inactive, or nothing had a wording. Either way
            // there is no CV to make, and saying so beats an empty page.
            return Result.err(new PipelineError.InsufficientProfile(
                    CompletenessCalculator.of(head, tree), List.of("atoms")));
        }
        if (built.estimatedAtoms() > 0) {
            // Counts, never content. A generation full of estimates is one the
            // measurement did not reach (Bolum 26.5).
            log.info("Selecting with {} estimated costs and {} atoms with no wording",
                    built.estimatedAtoms(), built.withoutWording());
        }

        return pipeline.run(head, tree, built.request(),
                options.customization(), options.locale());
    }

    /**
     * The preflight of Bolum 25.2: is there a profile here at all?
     *
     * <p>Deliberately structural rather than a percentage. Completeness is a
     * nudge on a progress bar; what stops a generation is having nothing to
     * print, and a percentage threshold would refuse profiles that would have
     * rendered perfectly well.
     */
    private static Result<Void> checkEnoughToWorkWith(Profile head, ProfileTree tree) {
        List<String> missing = new ArrayList<>();
        if (tree.atomCount() == 0) {
            missing.add("atoms");
        }
        if (tree.sections().isEmpty()) {
            missing.add("sections");
        }
        if (missing.isEmpty()) {
            return Result.ok(null);
        }
        return Result.err(new PipelineError.InsufficientProfile(
                CompletenessCalculator.of(head, tree), missing));
    }
}
