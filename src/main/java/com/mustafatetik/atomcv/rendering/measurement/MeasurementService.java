package com.mustafatetik.atomcv.rendering.measurement;

import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.rendering.latex.LatexDocumentRenderer;
import com.mustafatetik.atomcv.rendering.model.MeasurementRequest;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * One compilation, every atom (Bolum 26.2).
 *
 * <p>Compiling each atom on its own would mean two hundred runs for one
 * profile. Instead the whole batch goes into a single document that prints
 * nothing and reports sizes, and TeX — the only thing that knows how wide a
 * word is once hyphenation and justification have had their say — measures
 * them all at once.
 */
@Service
public class MeasurementService {

    private final LatexDocumentRenderer renderer;
    private final LatexCompilerClient compiler;

    public MeasurementService(LatexDocumentRenderer renderer, LatexCompilerClient compiler) {
        this.renderer = renderer;
        this.compiler = compiler;
    }

    /**
     * @return what each item measured, keyed as the request keyed it. An item
     *         missing from the result was not measured — the caller decides
     *         whether that is worth failing over, because the answer differs
     *         between a background job and a generation about to start.
     */
    public Map<String, RenderCost> measure(MeasurementRequest request) {
        if (request.items().isEmpty()) {
            return Map.of();
        }
        String texLog = compiler.measure(renderer.renderMeasurement(request).value());
        return TexLogParser.parseCosts(texLog);
    }
}
