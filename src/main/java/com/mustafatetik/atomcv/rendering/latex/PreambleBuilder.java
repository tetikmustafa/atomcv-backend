package com.mustafatetik.atomcv.rendering.latex;

import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.util.Locale;

/**
 * The preamble both documents share (Bolum 22.5).
 *
 * <p>One method, called from both render paths. That is the whole design: a
 * measurement is only true if it was taken under the geometry the final
 * document uses, and the cheapest way to guarantee that is to have one place
 * that can produce it.
 *
 * <p>No user string appears here. The font comes from an enum, the colour from
 * a validated value object, the numbers from range-checked fields and the
 * template body from a registry (Bolum 22.5).
 */
public final class PreambleBuilder {

    private PreambleBuilder() {
    }

    public static String build(TemplateCustomization customization) {
        if (!TemplateRegistry.exists(customization.baseTemplateId())) {
            throw new IllegalArgumentException("No such template");
        }
        // Locale.ROOT: absolute rule 7. Under a Turkish locale "%.1f" writes
        // "10,0" and the document does not compile.
        return String.format(Locale.ROOT, """
                \\documentclass[letterpaper,%.0fpt]{article}
                \\usepackage{fontspec}
                \\setmainfont{%s}
                \\usepackage[margin=%.2fin]{geometry}
                \\usepackage{xcolor}
                \\linespread{%.2f}
                \\definecolor{accent}{HTML}{%s}
                \\newlength{\\measurewidth}\\setlength{\\measurewidth}{\\textwidth}
                %s""",
                customization.fontSizePt(),
                customization.fontFamily().latexName(),
                customization.marginInches(),
                customization.lineSpacing(),
                customization.accentColor().value(),
                TemplateRegistry.baseOf(customization.baseTemplateId()));
    }
}
