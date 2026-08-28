package com.mustafatetik.atomcv.generation.pipeline;

import com.mustafatetik.atomcv.compilation.CompilationException;
import com.mustafatetik.atomcv.compilation.CompiledDocument;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.generation.render.RenderPhase;
import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.selection.SelectionPhase;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.rendering.DocumentRenderer;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.error.CompilationFailureKind;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Faz C to Faz F: choose, render, compile, and check the result (Bolum 20-23).
 *
 * <p>Faz D sits between the choosing and the rendering, and it is asked once
 * per atom however many times the loop goes round: a document that came out
 * too long selects again from a smaller budget, and the atoms that survive
 * that are the ones already rewritten. Paying for them twice would be paying
 * for the same sentences twice.
 *
 * <p>The page limit is a promise, and measurement alone cannot keep it: a
 * font's metrics are exact but a paragraph's line breaks are the compiler's
 * decision. So the compiled document is counted, and a document that came out
 * too long sends the budget back to selection reduced rather than sending the
 * text to an LLM to be shortened — Faz F never asks for new words
 * (Bolum 23.1).
 */
@Service
public class GenerationPipeline {

    private static final Logger log = LoggerFactory.getLogger(GenerationPipeline.class);

    /** Bolum 23.1: two goes at shrinking, then the user is told. */
    static final int MAX_RETRIES = 2;

    /** How much of the page is given up per retry. */
    static final double BUDGET_STEP = 0.95;

    private final DocumentRenderer renderer;
    private final LatexCompilerClient compiler;
    private final MeterRegistry meters;

    GenerationPipeline(
            DocumentRenderer renderer, LatexCompilerClient compiler, MeterRegistry meters) {
        this.renderer = renderer;
        this.compiler = compiler;
        this.meters = meters;
    }

    /**
     * @param request what selection may choose from, already scored and costed
     * @param rewriter Faz D, or {@link ContentRewriter#none()} in general mode
     *                 where there is no posting to write towards
     * @param maxPages the promise being kept — the same number the request was
     *                 built with, and the one the compiled document is checked
     *                 against
     */
    public Result<GeneratedDocument> run(
            Profile profile,
            ProfileTree tree,
            SelectionRequest request,
            ContentRewriter rewriter,
            TemplateCustomization customization,
            Locale contentLanguage) {

        int maxPages = request.maxPages();
        double factor = 1.0;
        int lastPageCount = 0;
        RewrittenContent rewritten = RewrittenContent.none();

        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            Result<SelectionState> selection =
                    SelectionPhase.select(request.withBudgetFactor(factor));
            if (selection instanceof Result.Err<SelectionState> refused) {
                return Result.err(refused.error());
            }
            SelectionState state = selection.orElseThrow();

            // Faz D. It answers with a CV whatever happens to it, so there is
            // no branch here for a rewrite that failed (Bolum 21.5).
            rewritten = rewriter.rewrite(state, rewritten);

            RenderRequest renderRequest = RenderPhase.build(
                    profile, tree, state, rewritten, customization, contentLanguage);

            CompiledDocument document;
            try {
                document = compiler.compile(renderer.renderFinal(renderRequest).value());
            } catch (CompilationException failed) {
                return Result.err(new PipelineError.CompilationFailed(
                        failed.kind(), failed.log()));
            }
            meters.counter("generation.compile.attempts").increment();
            lastPageCount = document.pageCount();

            if (document.pageCount() <= maxPages) {
                return Result.ok(new GeneratedDocument(
                        document.pdf(), document.pageCount(), state, renderRequest,
                        attempt, factor));
            }

            // Bolum 23.1: a rising rate here means the measurement layer is
            // wrong, not that users write too much.
            meters.counter("generation.budget.overshoot").increment();
            log.info("Document ran to {} pages against a limit of {}; shrinking the budget",
                    document.pageCount(), maxPages);
            factor *= BUDGET_STEP;
        }

        return Result.err(new PipelineError.PageLimitExceeded(lastPageCount, maxPages));
    }
}
