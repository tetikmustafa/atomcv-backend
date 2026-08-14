package com.mustafatetik.atomcv.rendering;

import com.mustafatetik.atomcv.rendering.model.MeasurementRequest;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.model.RenderedSource;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.Set;

/**
 * A format the profile can be rendered into (Bolum 22.2).
 *
 * <p>Two documents come out of one implementation, and the contract between
 * them is the point: whatever preamble the final document uses, the
 * measurement document uses the same one. A measurement taken under different
 * geometry is not a measurement, and the page guarantee rests on it.
 */
public interface DocumentRenderer {

    /** {@code latex}, {@code html}, {@code docx} — what the output is. */
    String formatId();

    Set<String> supportedTemplates();

    /** The document a user downloads. */
    RenderedSource renderFinal(RenderRequest request);

    /**
     * A document that prints nothing and reports heights (Bolum 26). It exists
     * so that selection can know what fits before anything is generated.
     */
    RenderedSource renderMeasurement(MeasurementRequest request);

    /**
     * What a page of this customization holds, if it has been measured.
     *
     * <p>Bolum 22.2 returns a model unconditionally; this returns an empty
     * optional for a customization nobody has calibrated. A capacity that was
     * guessed rather than measured breaks the page guarantee without saying
     * so, and that is the one failure the product cannot afford (EK D.8.3).
     */
    java.util.Optional<CapacityModel> capacity(TemplateCustomization customization);

    default boolean supports(TemplateCustomization customization) {
        return supportedTemplates().contains(customization.baseTemplateId());
    }
}
