package com.mustafatetik.atomcv.rendering.latex;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.DocumentRenderer;
import com.mustafatetik.atomcv.rendering.model.MeasurementRequest;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.model.RenderedSource;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The LaTeX renderer (Bolum 22).
 *
 * <p>Both documents begin with the same call to {@link PreambleBuilder}, and a
 * test asserts they still do. Everything that follows is deterministic: the
 * same request produces the same bytes, which is what makes a measurement
 * worth storing.
 */
@Component
public class LatexDocumentRenderer implements DocumentRenderer {

    /**
     * The measurement box. Not {@code \mbox}: that is already a LaTeX command
     * and {@code \newsavebox{\mbox}} stops the run with "already defined" —
     * which is what Bolum 22.4's snippet does (EK D.8.1).
     */
    private static final String BOX = "\\measurebox";

    @Override
    public String formatId() {
        return "latex";
    }

    @Override
    public java.util.Set<String> supportedTemplates() {
        return TemplateRegistry.ids();
    }

    @Override
    public RenderedSource renderFinal(RenderRequest request) {
        var out = new StringBuilder(PreambleBuilder.build(request.customization()));
        out.append("\\begin{document}\n");

        RenderRequest.ProfileHeader header = request.header();
        if (!header.name().isBlank()) {
            out.append("\\atomcvName{").append(LatexEscaper.escape(header.name())).append("}\n");
        }
        if (header.headline() != null && !header.headline().isBlank()) {
            out.append("\\atomcvContact{")
                    .append(LatexEscaper.escape(header.headline()))
                    .append("}\n");
        }
        if (!header.contactLines().isEmpty()) {
            out.append("\\atomcvContact{")
                    .append(String.join(" $\\cdot$ ", header.contactLines().stream()
                            .map(LatexEscaper::escape)
                            .toList()))
                    .append("}\n");
        }

        for (RenderRequest.RenderableSection section : request.sections()) {
            out.append("\n\\section*{").append(LatexEscaper.escape(section.title())).append("}\n");
            bullets(out, section.atoms());

            for (RenderRequest.RenderableEntry entry : section.entries()) {
                out.append("\\atomcvEntry{").append(LatexEscaper.escape(entry.title()))
                        .append("}{").append(LatexEscaper.escape(entry.organization()))
                        .append("}{").append(LatexEscaper.escape(entry.location()))
                        .append("}{").append(LatexEscaper.escape(entry.dateRange()))
                        .append("}\n");
                bullets(out, entry.atoms());
            }
        }

        out.append("\\end{document}\n");
        return new RenderedSource(out.toString());
    }

    /**
     * A document that prints nothing and reports heights (Bolum 22.4).
     *
     * <p>Three things have to match the final document or the numbers are
     * fiction: the preamble, the width the content is set at, and the
     * environment it is set inside. All three come from the same places the
     * final render uses them.
     */
    @Override
    public RenderedSource renderMeasurement(MeasurementRequest request) {
        var out = new StringBuilder(PreambleBuilder.build(request.customization()));
        out.append("\\begin{document}\n")
                .append("\\newsavebox{").append(BOX).append("}\n");

        for (MeasurementRequest.MeasurableItem item : request.items()) {
            out.append("\\begin{itemize}\n")
                    .append("\\savebox{").append(BOX).append("}{\\parbox{\\measurewidth}{")
                    .append(LatexInlineRenderer.render(item.content()))
                    .append("}}\n")
                    .append("\\typeout{ATOMCOST|").append(item.key())
                    .append("|\\the\\ht").append(BOX)
                    .append("|\\the\\dp").append(BOX).append("}\n")
                    .append("\\end{itemize}\n");
        }

        out.append("\\end{document}\n");
        return new RenderedSource(out.toString());
    }

    private static void bullets(StringBuilder out, List<RichContent> atoms) {
        if (atoms.isEmpty()) {
            return;
        }
        out.append("\\begin{itemize}\n");
        for (RichContent atom : atoms) {
            out.append("\\item ").append(LatexInlineRenderer.render(atom)).append('\n');
        }
        out.append("\\end{itemize}\n");
    }
}
