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
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.generation.validation.AtsCheck;
import com.mustafatetik.atomcv.generation.validation.AtsReport;
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
 * text to an LLM to be shortened — Faz F never asks for new words.
 */
@Service
public class GenerationPipeline {

    private static final Logger log = LoggerFactory.getLogger(GenerationPipeline.class);

    /** Two goes at shrinking, then the user is told. */
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
        // The request's own, not 1.0. A run against an estimated capacity
        // arrives asking for less of the page than there is, and starting the
        // loop at one would have thrown that away on the first attempt -- the
        // one attempt most documents ever take.
        double factor = request.budgetFactor();
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
            // no branch here for a rewrite that failed.
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
            meters.counter("generation.compile.attempts",
                    "template", customization.baseTemplateId()).increment();
            lastPageCount = document.pageCount();
            recordDrift(state, request, customization, document.pageCount());

            if (document.pageCount() <= maxPages) {
                reportAts(document.pdf(), renderRequest);
                return Result.ok(new GeneratedDocument(
                        document.pdf(), document.pageCount(), state, renderRequest,
                        attempt, factor, rewritten));
            }

            // A rising rate here means the measurement layer is wrong, not
            // that users write too much.
            meters.counter("generation.budget.overshoot",
                    "template", customization.baseTemplateId()).increment();
            log.info("Document ran to {} pages against a limit of {}; shrinking the budget",
                    document.pageCount(), maxPages);
            factor *= BUDGET_STEP;
        }

        return Result.err(new PipelineError.PageLimitExceeded(lastPageCount, maxPages));
    }

    /**
     * The calibration signal, in the only resolution production can measure
     * it.
     *
     * <p><strong>Not the drift percentage Bolum 26.6 writes.</strong> That one
     * divides a measured content height by the predicted one, and there is no
     * measured height here: the note records that no {@code pdfAnalyzer}
     * exists and that the page count arrives as the compiler's {@code
     * X-Page-Count} header. A height would cost a second compilation of every
     * document, and the measurement document that can produce one shares a
     * preamble with the page rather than being it.
     *
     * <p>What can be compared is pages: how many the budget said this would
     * take against how many came back. Zero is the ordinary reading and the
     * distribution is the diagnostic — a template whose mean walks away from
     * zero has a measurement layer that is wrong, which is exactly what
     * Bolum 26.6 wants this number for. It is coarse, and it is honest about
     * being coarse.
     *
     * <p><strong>Nothing acts on it.</strong> Bolum 26.6 also asks for the
     * safety margin to widen itself when the drift crosses three percent, and
     * that is deliberately not here: a guarantee that adjusts itself on a
     * signal measured in whole pages would move on one bad document and would
     * have no way to move back. The number goes to an operator.
     */
    private void recordDrift(SelectionState state, SelectionRequest request,
            TemplateCustomization customization, int actualPages) {

        double pageHeightPt = request.capacity().pageTextHeightPt();
        if (pageHeightPt <= 0) {
            return;
        }
        double usedPt = state.budget().fixedPt() + state.budget().usedPt();
        int predictedPages = Math.max(1, (int) Math.ceil(usedPt / pageHeightPt));

        meters.summary("generation.pages.drift",
                        "template", customization.baseTemplateId())
                .record(actualPages - (double) predictedPages);
    }

    /**
     * And it is watched rather than acted on.
     *
     * <p>The page is already paid for and already fits; anything this finds is
     * a defect in our template or our fonts, and taking the document away from
     * the person would be answering our own bug with their loss. What it is
     * for is the counter: a rate that moves after a template change means that
     * change broke machine readability, and nothing upstream would have said
     * so — the budget, the fit report and the validators all measure the CV
     * before it is a PDF.
     *
     * <p>Counts only in the log line. A section heading is the user's own
     * wording (absolute rule 4).
     */
    private void reportAts(byte[] pdf, RenderRequest rendered) {
        AtsReport ats = AtsCheck.of(pdf, rendered);
        meters.counter(ats.clean() ? "generation.ats.clean" : "generation.ats.defect")
                .increment();
        if (!ats.clean()) {
            log.warn("The generated PDF does not read back cleanly: {}", ats);
        }
    }
}
