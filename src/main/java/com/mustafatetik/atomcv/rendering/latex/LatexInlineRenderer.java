package com.mustafatetik.atomcv.rendering.latex;

import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;

/**
 * Runs to LaTeX (Bolum 22.3).
 *
 * <p>Marks are semantic, so this is where a template decides what they look
 * like: classic renders a technology and a metric the same way, and a template
 * that wanted the metric in the accent colour would change here and nowhere
 * else.
 */
public final class LatexInlineRenderer {

    private LatexInlineRenderer() {
    }

    public static String render(RichContent content) {
        var out = new StringBuilder();
        for (Run run : content.runs()) {
            out.append(render(run));
        }
        return out.toString();
    }

    private static String render(Run run) {
        String text = LatexEscaper.escape(run.text());
        for (Mark mark : run.marks()) {
            text = apply(mark, text, run.href());
        }
        return text;
    }

    private static String apply(Mark mark, String text, String href) {
        // Bolum 22.3 said italic for emphasis, and the reference document this
        // template was measured against writes its technologies bold. The two
        // disagreed for one reason: extraction marks almost everything
        // EMPHASIS (§ 31.5.1) rather than TECHNOLOGY, so the page italicised
        // what the document it copies sets bold. Bold is the decision (2026-09-09)
        // and it costs a measurement: bold sets wider, so the golden set's page
        // costs were re-recorded with it.
        if (Mark.TECHNOLOGY.equals(mark) || Mark.METRIC.equals(mark)
                || Mark.EMPHASIS.equals(mark)) {
            return "\\textbf{" + text + "}";
        }
        if (Mark.LINK.equals(mark)) {
            return "\\href{" + LatexEscaper.escapeUrl(href) + "}{" + text + "}";
        }
        // ORGANIZATION carries meaning for scoring, not for the page — and an
        // unknown mark falls here too. Forward compatibility (Bolum 16.2):
        // content written by a newer build renders as plain text rather than
        // failing to render at all.
        return text;
    }
}
