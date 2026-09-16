package com.mustafatetik.atomcv.rendering.latex;

import com.mustafatetik.atomcv.compilation.CompilationException;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.rendering.DocumentWriter;
import com.mustafatetik.atomcv.rendering.OutputFormat;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import org.springframework.stereotype.Component;

/**
 * The typeset document: render to LaTeX, then compile.
 *
 * <p>Two steps and one download. They were spelled out at the call site in the
 * generation module, which is why that module had to hold a compiler client
 * as well as a renderer for a question about a file format.
 *
 * <p><strong>The only writer that can fail.</strong> A compilation failure is
 * not a bug to throw at the caller: the row is fine, the source is fine, and
 * something in the toolchain is not. It travels as a {@code PipelineError} so
 * the caller can present it with a status code and a resolution.
 */
@Component
public class PdfDocumentWriter implements DocumentWriter {

    private final LatexDocumentRenderer renderer;
    private final LatexCompilerClient compiler;

    PdfDocumentWriter(LatexDocumentRenderer renderer, LatexCompilerClient compiler) {
        this.renderer = renderer;
        this.compiler = compiler;
    }

    @Override
    public OutputFormat format() {
        return OutputFormat.PDF;
    }

    @Override
    public Result<byte[]> bytesFor(RenderRequest request) {
        try {
            return Result.ok(compiler.compile(renderer.renderFinal(request).value()).pdf());
        } catch (CompilationException failed) {
            return Result.err(new PipelineError.CompilationFailed(failed.kind(), failed.log()));
        }
    }
}
