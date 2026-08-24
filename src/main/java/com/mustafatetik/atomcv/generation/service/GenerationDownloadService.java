package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.compilation.CompilationException;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.RenderedContent;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.rendering.DocumentRenderer;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Handing back a document that was already made (EK D.6.3).
 *
 * <p>Stage 2 stores no bytes — {@code pdf_key} is for R2 and R2 arrives in
 * Stage 3 — so a download is a re-render. That is not a workaround: EK D.6.3
 * already says the PDF is always reproducible, and here it is the whole
 * mechanism rather than the fallback for an expired one.
 *
 * <p><strong>It re-renders the snapshot, never the profile.</strong> The
 * selection names atoms by id and the text under those ids goes on being
 * edited; reading the profile again would hand back a different document from
 * the one that was sent to an employer, and nothing would say so. The content
 * snapshot is what the renderer was given the first time, so the second run has
 * the same input.
 *
 * <p>No LLM, no scoring, no selection. One compilation, and it is deterministic
 * — the same row produces the same bytes on any day.
 */
@Service
public class GenerationDownloadService {

    private final GenerationRepository generations;
    private final DocumentRenderer renderer;
    private final LatexCompilerClient compiler;

    GenerationDownloadService(GenerationRepository generations, DocumentRenderer renderer,
            LatexCompilerClient compiler) {

        this.generations = generations;
        this.renderer = renderer;
        this.compiler = compiler;
    }

    /** The user's own generation, or nothing. Absolute rule 3. */
    public Optional<Generation> find(UserContext user, UUID generationId) {
        return generations.findById(user, generationId);
    }

    /**
     * @return the PDF, or the compilation failure. Whether the row exists and
     *         whether it can be re-rendered at all are the caller's questions:
     *         both end the request with a status code and neither is a
     *         pipeline failure.
     */
    public Result<byte[]> render(Generation generation) {
        RenderedContent content = generation.getContentSnapshot();
        StoredSelection selection = generation.getSelectionState();
        var request = content.toRenderRequest(
                selection.customization(), java.util.Locale.forLanguageTag(selection.language()));

        try {
            return Result.ok(compiler.compile(renderer.renderFinal(request).value()).pdf());
        } catch (CompilationException failed) {
            return Result.err(
                    new PipelineError.CompilationFailed(failed.kind(), failed.log()));
        }
    }
}
