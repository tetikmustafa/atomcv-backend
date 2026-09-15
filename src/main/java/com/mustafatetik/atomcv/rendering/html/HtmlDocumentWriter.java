package com.mustafatetik.atomcv.rendering.html;

import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The same CV as one self-contained HTML file (Bolum 22.6, Bolum 1.2's fourth
 * claim).
 *
 * <p><strong>One file and nothing fetched.</strong> No stylesheet, no font, no
 * script, no image: the document is opened from a downloads folder, pasted
 * into a web form, or handed to a parser, and every one of those breaks on a
 * reference to something that is not there. The styling is one inline
 * {@code <style>} block derived from the same {@link TemplateCustomization}
 * the PDF used, so the two look like the same CV without the HTML pretending
 * to be a page.
 *
 * <p><strong>The page guarantee does not travel with it, and here it does not
 * even apply.</strong> The atoms are the ones that fit a LaTeX page; HTML has
 * no page at all. That is a weaker claim than the DOCX one of Bolum 22.6,
 * which is approximate — this one is simply about a different kind of
 * document, and the honest thing is to say so rather than to set a width and
 * call it a page.
 *
 * <p><strong>Structure over appearance, because the reader may be a
 * machine.</strong> Headings are {@code <h1>}/{@code <h2>}, bullets are
 * {@code <ul><li>}, and the contact line is one paragraph of text. Bolum 2.1
 * is about what an ATS does to a two-column layout with a table in it; the
 * cheapest way not to be that document is not to build one.
 *
 * <p>It walks exactly the tree {@code DocxDocumentWriter} walks, and neither
 * knows anything about selection (Bolum 22.2): no atom ids, no scores, no
 * locks reach a renderer.
 */
@Component
public class HtmlDocumentWriter {

    /** Roughly what {@code \Huge} sets a name at, as the DOCX writer also reads it. */
    private static final double NAME_SIZE_FACTOR = 2.0;

    private static final double HEADING_SIZE_OFFSET_PT = 2.0;

    public String write(RenderRequest request) {
        var html = new StringBuilder();
        RenderRequest.ProfileHeader profile = request.header();

        html.append("<!DOCTYPE html>\n<html lang=\"")
                .append(HtmlInlineRenderer.escape(request.contentLanguage().getLanguage()))
                .append("\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, ")
                .append("initial-scale=1\">\n<title>")
                .append(HtmlInlineRenderer.escape(profile.name()))
                .append("</title>\n")
                .append(style(request.customization()))
                .append("</head>\n<body>\n");

        header(html, profile);
        for (RenderRequest.RenderableSection section : request.sections()) {
            section(html, section);
        }

        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private static void header(StringBuilder html, RenderRequest.ProfileHeader profile) {
        html.append("<header>\n<h1>")
                .append(HtmlInlineRenderer.escape(profile.name()))
                .append("</h1>\n");

        if (StringUtils.hasText(profile.headline())) {
            html.append("<p class=\"headline\">")
                    .append(HtmlInlineRenderer.escape(profile.headline()))
                    .append("</p>\n");
        }

        String contact = profile.contactLines().stream()
                .map(RenderRequest.ContactLine::value)
                .filter(StringUtils::hasText)
                .map(HtmlInlineRenderer::escape)
                .reduce((left, right) -> left + " &middot; " + right)
                .orElse("");
        if (!contact.isBlank()) {
            html.append("<p class=\"contact\">").append(contact).append("</p>\n");
        }
        html.append("</header>\n");
    }

    private static void section(StringBuilder html, RenderRequest.RenderableSection section) {
        html.append("<section>\n<h2>")
                // Upper case with Locale.ROOT: absolute rule 7. A Turkish
                // default locale turns a section called "Sertifikalar" into
                // one nobody spelled.
                .append(HtmlInlineRenderer.escape(
                        section.title().toUpperCase(Locale.ROOT)))
                .append("</h2>\n");

        if (section.layout() == SectionLayout.INLINE_LIST) {
            inline(html, section);
            html.append("</section>\n");
            return;
        }

        boolean prose = section.layout() == SectionLayout.PARAGRAPH;
        atoms(html, section.atoms(), prose);
        for (RenderRequest.RenderableEntry entry : section.entries()) {
            entry(html, entry);
        }
        html.append("</section>\n");
    }

    private static void entry(StringBuilder html, RenderRequest.RenderableEntry entry) {
        html.append("<article>\n<p class=\"entry\"><strong>")
                .append(HtmlInlineRenderer.escape(entry.title()))
                .append("</strong>");
        if (StringUtils.hasText(entry.dateRange())) {
            html.append("<span class=\"dates\">")
                    .append(HtmlInlineRenderer.escape(entry.dateRange()))
                    .append("</span>");
        }
        html.append("</p>\n");

        String below = StringUtils.hasText(entry.organization()) ? entry.organization() : "";
        if (StringUtils.hasText(entry.location())) {
            below = below.isBlank() ? entry.location() : below + ", " + entry.location();
        }
        if (!below.isBlank()) {
            html.append("<p class=\"where\">")
                    .append(HtmlInlineRenderer.escape(below))
                    .append("</p>\n");
        }

        atoms(html, entry.atoms(), false);
        html.append("</article>\n");
    }

    /**
     * Bolum 33.4.1's labelled rows: a bold category up to the colon and plain
     * text after it, which is what the reference template prints and what a
     * person scans down the page for.
     */
    private static void inline(StringBuilder html, RenderRequest.RenderableSection section) {
        for (RenderRequest.RenderableEntry entry : section.entries()) {
            html.append("<p class=\"inline\"><strong>")
                    .append(HtmlInlineRenderer.escape(entry.title()))
                    .append(":</strong> ");
            for (RichContent atom : entry.atoms()) {
                html.append(plain(atom));
            }
            html.append("</p>\n");
        }
        for (RichContent atom : section.atoms()) {
            html.append("<p class=\"inline\">").append(plain(atom)).append("</p>\n");
        }
    }

    private static void atoms(StringBuilder html, java.util.List<RichContent> atoms,
            boolean prose) {

        if (atoms.isEmpty()) {
            return;
        }
        if (prose) {
            for (RichContent atom : atoms) {
                html.append("<p>").append(HtmlInlineRenderer.render(atom)).append("</p>\n");
            }
            return;
        }
        html.append("<ul>\n");
        for (RichContent atom : atoms) {
            html.append("<li>").append(HtmlInlineRenderer.render(atom)).append("</li>\n");
        }
        html.append("</ul>\n");
    }

    /**
     * Bolum 33.4.1 again: nothing else on an inline row is set in bold, because
     * seventy percent of a skill matrix emphasised is a matrix that emphasises
     * nothing. The marks are still in the content and still mean what they
     * mean; this layout prints them plainly.
     */
    private static String plain(RichContent atom) {
        return HtmlInlineRenderer.escape(atom.plainText());
    }

    /**
     * The customization, as far as it makes sense to carry it.
     *
     * <p>Font size, family and the accent colour travel; the margin does not,
     * because a margin is a statement about a page. A max width in its place
     * keeps a line readable in a browser window of any size, which is the same
     * intention in the medium this document is actually read in.
     *
     * <p>The font family is named with a fallback stack rather than loaded: a
     * downloaded file has no network, and a font that fails to load silently
     * is a document that looks broken for no reason a reader can see.
     */
    private static String style(TemplateCustomization customization) {
        return """
                <style>
                :root { color-scheme: light dark; }
                body {
                  font-family: %s;
                  font-size: %.1fpt;
                  line-height: %.2f;
                  max-width: 45rem;
                  margin: 2rem auto;
                  padding: 0 1rem;
                }
                h1 { font-size: %.1fpt; margin: 0 0 .2rem; }
                h2 {
                  font-size: %.1fpt;
                  text-transform: uppercase;
                  letter-spacing: .04em;
                  border-bottom: 1px solid #%s;
                  padding-bottom: .15rem;
                  margin: 1.4rem 0 .5rem;
                }
                header { margin-bottom: 1.2rem; }
                .headline, .contact { margin: .15rem 0; }
                .entry { margin: .6rem 0 .1rem; display: flex; justify-content: space-between;
                         gap: 1rem; }
                .where { margin: 0 0 .2rem; font-style: italic; }
                .dates { white-space: nowrap; }
                ul { margin: .2rem 0; padding-left: 1.2rem; }
                li { margin: .15rem 0; }
                p.inline { margin: .2rem 0; }
                a { color: inherit; }
                @media print { body { margin: 0; max-width: none; } }
                </style>
                """.formatted(
                fontStack(customization.fontFamily()),
                customization.fontSizePt(),
                customization.lineSpacing(),
                customization.fontSizePt() * NAME_SIZE_FACTOR,
                customization.fontSizePt() + HEADING_SIZE_OFFSET_PT,
                customization.accentColor().value());
    }

    /**
     * A stack whose first entry is the family the PDF used and whose last is a
     * generic the reader certainly has. Bolum 5.5's whitelist is about what
     * XeLaTeX may load; here it decides only what to ask for first.
     */
    private static String fontStack(FontFamily family) {
        String generic = switch (family) {
            case SANS -> "sans-serif";
            case MODERN, SERIF, BOOK -> "serif";
        };
        return "'" + family.latexName() + "', " + generic;
    }
}
