package com.mustafatetik.atomcv.rendering.latex;

import com.mustafatetik.atomcv.rendering.DocumentWriter;
import com.mustafatetik.atomcv.rendering.OutputFormat;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.shared.error.Result;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * What the compiler was given.
 *
 * <p>The same source the PDF is made from, so a person who would rather
 * typeset their own CV gets the document they downloaded rather than
 * retyping it.
 *
 * <p>No compiler, so nothing to fail: this is the step before the one that
 * can.
 */
@Component
public class LatexSourceDocumentWriter implements DocumentWriter {

    private final LatexDocumentRenderer renderer;

    LatexSourceDocumentWriter(LatexDocumentRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public OutputFormat format() {
        return OutputFormat.SOURCE;
    }

    @Override
    public Result<byte[]> bytesFor(RenderRequest request) {
        return Result.ok(
                renderer.renderFinal(request).value().getBytes(StandardCharsets.UTF_8));
    }
}
