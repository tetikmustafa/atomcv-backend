package com.mustafatetik.atomcv.rendering;

import com.mustafatetik.atomcv.rendering.model.MeasurementRequest;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.model.RenderedSource;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.Optional;

/**
 * A typesetting backend whose output can be measured before it is produced.
 *
 * <p>Two documents come out of one implementation, and the contract between
 * them is the point: whatever preamble the final document uses, the
 * measurement document uses the same one. A measurement taken under different
 * geometry is not a measurement, and the page guarantee rests on it.
 *
 * <p><strong>This is not "a format", and it used to say it was.</strong> The
 * interface carried a {@code formatId()} and a {@code supportedTemplates()},
 * and the javadoc on the first said {@code latex}, {@code html}, {@code docx}
 * — three formats, one implementation, and not one caller for either method in
 * the whole repository. The abstraction it advertised lived nowhere: the
 * generation module reached the other two formats by holding their concrete
 * classes.
 *
 * <p>So the format abstraction moved to {@link DocumentWriter}, where all
 * three really do fit, and what is left here is the job this interface was
 * actually doing — render source, render the measurement document for the same
 * geometry, report the capacity that was measured. HTML has no page and POI
 * <em>is</em> the Word document, so neither can answer any of those, and
 * pretending otherwise is what put two writers outside the contract in the
 * first place.
 */
public interface DocumentRenderer {

    /** The document a user downloads, as source for a compiler. */
    RenderedSource renderFinal(RenderRequest request);

    /**
     * A document that prints nothing and reports heights. It exists so that
     * selection can know what fits before anything is generated.
     */
    RenderedSource renderMeasurement(MeasurementRequest request);

    /**
     * What a page of this customization holds, if it has been measured.
     *
     * <p>The interface was written to return a model unconditionally; this
     * returns an empty optional for a customization nobody has calibrated. A
     * capacity that was guessed rather than measured breaks the page guarantee
     * without saying so, and that is the one failure the product cannot
     * afford.
     */
    Optional<CapacityModel> capacity(TemplateCustomization customization);
}
