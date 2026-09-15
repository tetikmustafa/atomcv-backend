package com.mustafatetik.atomcv.rendering.html;

import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;

/**
 * One {@link RichContent} as inline HTML.
 *
 * <p><strong>The same marks, a different alphabet.</strong> Bolum 12.3 is the
 * reason this class is three lines of decision and no interpretation: a mark
 * is semantic, so each renderer decides what it looks like and none of them
 * decides what it means. What is bold here is what is bold in LaTeX, for the
 * same reason and by the same list.
 *
 * <p><strong>Escaping is central and is the whole defence.</strong> Bolum 42.3
 * says it in one line: "LaTeX'te güvenliydi" varsayımı digerlerine tasinmaz.
 * A CV is user content and this output is HTML, so every character that means
 * something to a parser is encoded here and nowhere else — the same shape as
 * {@code LatexEscaper}, against a different grammar.
 *
 * <p>An unknown mark renders as plain text. A newer version's markings must
 * not make an older renderer fail.
 */
public final class HtmlInlineRenderer {

    private HtmlInlineRenderer() {
    }

    public static String render(RichContent content) {
        var html = new StringBuilder();
        for (Run run : content.runs()) {
            html.append(render(run));
        }
        return html.toString();
    }

    private static String render(Run run) {
        String text = escape(run.text());

        // A link is the one mark that wraps rather than emphasises, so it is
        // applied last and outermost -- bold inside a link reads correctly,
        // a link inside bold is the same thing written the harder way.
        boolean bold = false;
        boolean link = false;
        for (Mark mark : run.marks()) {
            if (Mark.TECHNOLOGY.equals(mark) || Mark.METRIC.equals(mark)
                    || Mark.EMPHASIS.equals(mark)) {
                bold = true;
            } else if (Mark.LINK.equals(mark)) {
                link = true;
            }
            // ORGANIZATION and anything this version has never heard of fall
            // through as plain text, which is Bolum 16.2's promise.
        }

        if (bold) {
            text = "<strong>" + text + "</strong>";
        }
        if (link) {
            text = "<a href=\"" + escape(run.href()) + "\">" + text + "</a>";
        }
        return text;
    }

    /**
     * The five characters that change a document's structure, and nothing
     * else.
     *
     * <p>Quotes are encoded because this same method escapes an {@code href},
     * which sits inside an attribute: a URL carrying a quote would otherwise
     * close the attribute and open whatever came after it. That is the entire
     * distance between an escaper and an injection.
     */
    static String escape(String text) {
        if (text == null) {
            return "";
        }
        var escaped = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }
}
