package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.compilation.CompilationException;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.rendering.docx.DocxDocumentWriter;
import com.mustafatetik.atomcv.rendering.html.HtmlDocumentWriter;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
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
 * Handing back a document that was already made.
 *
 * <p>Stage 2 stores no bytes — {@code pdf_key} is for R2 and R2 arrives in
 * Stage 3 — so a download is a re-render. That is not a workaround: the PDF is
 * always reproducible by design, and here it is the whole mechanism rather
 * than the fallback for an expired one.
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
    private final DocxDocumentWriter docx;
    private final HtmlDocumentWriter html;
    private final DocumentRenderer renderer;
    private final LatexCompilerClient compiler;

    GenerationDownloadService(GenerationRepository generations, DocumentRenderer renderer,
            LatexCompilerClient compiler, DocxDocumentWriter docx, HtmlDocumentWriter html) {
        this.docx = docx;
        this.html = html;

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
        try {
            return Result.ok(compiler.compile(
                    renderer.renderFinal(requestFor(generation)).value()).pdf());
        } catch (CompilationException failed) {
            return Result.err(
                    new PipelineError.CompilationFailed(failed.kind(), failed.log()));
        }
    }

    /**
     * The same CV as a Word document.
     *
     * <p>No compiler and no failure to report: POI writes the package
     * itself, so unlike the PDF this cannot come back as a compilation
     * error.
     *
     * <p><strong>The page guarantee does not travel with it.</strong> The
     * atoms are the ones that fit a LaTeX page, and Word may set them in a
     * little more or less room -- the guarantee is approximate here and the
     * frontend is told to say so (B-094).
     */
    public byte[] renderDocx(Generation generation) {
        return docx.write(requestFor(generation));
    }

    /**
     * The same CV as one self-contained HTML file.
     *
     * <p>No compiler here either, and no page: HTML has none, so the guarantee
     * does not merely become approximate the way it does for Word -- it does
     * not apply. The atoms are the ones that fitted a typeset page and this is
     * a different kind of document made of them.
     */
    public String renderHtml(Generation generation) {
        return html.write(requestFor(generation));
    }

    /**
     * What the compiler was given ({@code format=source}).
     *
     * <p><strong>Reading it is not the Layer C.</strong> That rule refuses to
     * let a person <em>write</em> LaTeX, because user-authored markup reaching
     * a compiler is a remote execution surface. Handing back what this product
     * generated is the opposite direction and carries none of it: nothing is
     * read back in, and a person who wants to typeset their own CV by hand
     * should not have to retype it.
     *
     * <p>The same source the PDF is made from, so a person compiling it
     * themselves gets the document they downloaded.
     */
    public String renderSource(Generation generation) {
        return renderer.renderFinal(requestFor(generation)).value();
    }

    /**
     * What was printed, read off the snapshot rather than off today's profile.
     *
     * <p>Shared by both formats on purpose: two readings of one row would be
     * two documents, and the whole reason the snapshot exists is that the text
     * under an atom keeps changing.
     */
    private static RenderRequest requestFor(Generation generation) {
        RenderedContent content = generation.getContentSnapshot();
        StoredSelection selection = generation.getSelectionState();
        return content.toRenderRequest(
                selection.customization(), java.util.Locale.forLanguageTag(selection.language()));
    }
}
