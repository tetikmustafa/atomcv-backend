package com.mustafatetik.atomcv.rendering.latex;

import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.DocumentRenderer;
import com.mustafatetik.atomcv.rendering.model.MeasurementRequest;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.model.RenderedSource;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    public Optional<CapacityModel> capacity(TemplateCustomization customization) {
        return TemplateRegistry.capacityOf(customization);
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
                            .map(LatexDocumentRenderer::contact)
                            .toList()))
                    .append("}\n");
        }

        for (RenderRequest.RenderableSection section : request.sections()) {
            out.append("\n\\section*{").append(LatexEscaper.escape(section.title())).append("}\n");
            section(out, section);
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
            // An \item, and the box set at \linewidth. Bolum 22.4 opens an
            // itemize with neither: LaTeX stops at "perhaps a missing \item",
            // and \textwidth would measure content at a width no bullet ever
            // gets (EK D.8.3).
            // The same environment as the page. A bullet is printed inside
            // \resumeItemListStart and \linewidth is narrower there than
            // \textwidth, which is Bolum 22.4's own rule: the preamble, the
            // width and the environment all match, or the numbers are fiction.
            // The same content the page will carry, marks and all. An
            // INLINE_LIST row reaches the page with its label in bold, so it is
            // measured that way too: \resumeInlineList and \resumeItemListStart
            // both set their contents at the same \linewidth, and the only
            // difference between the two that costs points is this one.
            out.append("\\resumeItemListStart\n")
                    .append("\\item\\savebox{").append(BOX)
                    .append("}{\\parbox{\\linewidth}{")
                    .append(item.layout() == SectionLayout.INLINE_LIST
                            ? InlineRow.render(item.content())
                            : LatexInlineRenderer.render(item.content()))
                    .append("}}\\usebox{").append(BOX).append("}\n")
                    .append("\\typeout{ATOMCOST|").append(item.key())
                    .append("|\\the\\ht").append(BOX)
                    .append("|\\the\\dp").append(BOX).append("}\n")
                    .append("\\resumeItemListEnd\n");
        }

        out.append("\\end{document}\n");
        return new RenderedSource(out.toString());
    }

    /**
     * A document that reports the template's own geometry (Bolum 26.4).
     *
     * <p>It prints probes and asks TeX where it is on the page after each one:
     * the difference between two positions is what that piece of furniture
     * costs. Running it is how the numbers in {@code TemplateRegistry} came to
     * exist, and re-running it is how a change to the template is noticed.
     *
     * <p>Same preamble as everything else, for the same reason.
     */
    public RenderedSource renderCalibration(TemplateCustomization customization) {
        return new RenderedSource(PreambleBuilder.build(customization) + """
                \\begin{document}
                \\typeout{CALIB|textheight|\\the\\textheight}
                \\typeout{CALIB|textwidth|\\the\\textwidth}
                \\typeout{CALIB|baselineskip|\\the\\baselineskip}
                \\typeout{CALIB|start|\\the\\pagetotal}
                \\atomcvName{Probe}
                \\atomcvContact{Probe}
                \\atomcvContact{Probe}
                \\typeout{CALIB|afterHeaderBlock|\\the\\pagetotal}
                \\section*{Probe}
                \\typeout{CALIB|afterSection|\\the\\pagetotal}
                \\resumeItemListStart\\resumeItem{Probe}\\resumeItemListEnd
                \\typeout{CALIB|afterListUnderSection|\\the\\pagetotal}
                \\section*{Probe}
                \\typeout{CALIB|beforeThreeUnderSection|\\the\\pagetotal}
                \\resumeItemListStart\\resumeItem{Probe}\\resumeItem{Probe}%
                \\resumeItem{Probe}\\resumeItemListEnd
                \\typeout{CALIB|afterThreeUnderSection|\\the\\pagetotal}
                \\section*{Probe}
                \\typeout{CALIB|afterThirdSection|\\the\\pagetotal}
                \\resumeSubHeadingListStart
                \\resumeSubheading{Probe}{Probe}{Probe}{Probe}
                \\resumeSubHeadingListEnd
                \\typeout{CALIB|afterEntry|\\the\\pagetotal}
                \\resumeItemListStart\\resumeItem{Probe}\\resumeItemListEnd
                \\typeout{CALIB|afterOneItem|\\the\\pagetotal}
                \\section*{Probe}
                \\resumeSubHeadingListStart
                \\resumeSubheading{Probe}{Probe}{Probe}{Probe}
                \\resumeSubHeadingListEnd
                \\typeout{CALIB|beforeThreeItems|\\the\\pagetotal}
                \\resumeItemListStart\\resumeItem{Probe}\\resumeItem{Probe}%
                \\resumeItem{Probe}\\resumeItemListEnd
                \\typeout{CALIB|afterThreeItems|\\the\\pagetotal}
                \\section*{Probe}
                \\typeout{CALIB|afterSecondSection|\\the\\pagetotal}
                \\resumeSubHeadingListStart
                \\resumeSubheading{Probe}{Probe}{Probe}{Probe}
                \\resumeSubHeadingListEnd
                \\typeout{CALIB|afterSecondEntry|\\the\\pagetotal}
                \\resumeItemListStart\\resumeItem{Probe}\\resumeItemListEnd
                \\typeout{CALIB|afterSecondList|\\the\\pagetotal}
                \\resumeSubHeadingListStart
                \\resumeSubheading{Probe}{Probe}{Probe}{Probe}
                \\resumeSubHeadingListEnd
                \\typeout{CALIB|afterEntryFollowingAList|\\the\\pagetotal}
                \\section*{Probe}
                \\typeout{CALIB|beforeInlineOne|\\the\\pagetotal}
                \\resumeInlineList{Probe
                }
                \\typeout{CALIB|afterInlineOne|\\the\\pagetotal}
                \\section*{Probe}
                \\typeout{CALIB|beforeInlineThree|\\the\\pagetotal}
                \\resumeInlineList{Probe \\\\
                Probe \\\\
                Probe
                }
                \\typeout{CALIB|afterInlineThree|\\the\\pagetotal}
                \\end{document}
                """);
    }

    /**
     * One contact field, labelled and linked.
     *
     * <p>The block used to be the bare values joined by a middle dot, which
     * reads as a list of strings rather than as a way to reach someone.
     *
     * <p>Both halves are escaped. The href is user text too — a website is
     * whatever the person typed — and an unescaped {@code %} or {@code #} in a
     * URL ends the compile or silently truncates the link.
     */
    private static String contact(RenderRequest.ContactLine line) {
        String value = LatexEscaper.escape(line.value());
        String shown = line.href().isBlank()
                ? value
                : "\\href{" + LatexEscaper.escapeUrl(line.href())
                        + "}{\\underline{" + value + "}}";
        return line.label().isBlank()
                ? shown
                : "\\textbf{" + LatexEscaper.escape(line.label()) + ":} " + shown;
    }

    /**
     * Bolum 33.4's layouts. Until this existed there was one: every section was
     * set as a bullet list whatever its column said, so a Tech Stack carrying
     * {@code INLINE_LIST} printed as bullets — or, once selection had dropped
     * it, not at all.
     *
     * <p>{@code TWO_COLUMN} is deliberately absent and falls through to the
     * entry list. Bolum 33.5 makes Classic single-column on purpose: "an ATS
     * extracts text, and a layout that reads well to a person but scrambles
     * under extraction is a CV that never reaches one." Honouring it here would
     * be this file overruling that decision quietly.
     */
    private static void section(StringBuilder out, RenderRequest.RenderableSection section) {
        if (section.layout() == SectionLayout.INLINE_LIST) {
            inlineList(out, section);
            return;
        }
        if (section.layout() == SectionLayout.PARAGRAPH) {
            paragraphs(out, section);
            return;
        }
        items(out, section.atoms());
        if (section.entries().isEmpty()) {
            return;
        }
        out.append("\\resumeSubHeadingListStart\n");
        for (RenderRequest.RenderableEntry entry : section.entries()) {
            heading(out, entry);
            items(out, entry.atoms());
        }
        out.append("\\resumeSubHeadingListEnd\n");
    }

    /**
     * A heading with a place and a period, or one with neither.
     *
     * <p>Chosen from the entry rather than from the section's title: a project
     * is the entry carrying no employer, no location and no dates, and asking
     * the data is steadier than asking a heading what it is called.
     */
    private static void heading(StringBuilder out, RenderRequest.RenderableEntry entry) {
        boolean bare = entry.organization().isBlank()
                && entry.location().isBlank()
                && entry.dateRange().isBlank();
        if (bare) {
            out.append("\\resumeProjectHeading{\\textbf{")
                    .append(LatexEscaper.escape(entry.title())).append("}}{}\n");
            return;
        }
        out.append("\\resumeSubheading{").append(LatexEscaper.escape(entry.title()))
                .append("}{").append(LatexEscaper.escape(entry.dateRange()))
                .append("}{").append(LatexEscaper.escape(entry.organization()))
                .append("}{").append(LatexEscaper.escape(entry.location()))
                .append("}\n");
    }

    /**
     * Prose under a heading, with no marker in front of it (Bolum 33.4).
     *
     * <p>One {@code \resumeItem} per paragraph, however long the paragraph is
     * and however many sentences it holds — a summary is a block, and nothing
     * here counts its sentences or looks for a place to break it. The document
     * this template was taken from writes its About exactly this way.
     *
     * <p>Entries are flattened into the same list. A section set as prose has
     * no headings to print: extraction has to put every atom somewhere and the
     * shape it is given has only entries, so it invents a title for the one it
     * makes — and an invented title above a paragraph is a line nobody wrote.
     * {@code ProfileWriter} already hangs a summary off its section for that
     * reason; this makes the renderer unable to print one even if a row
     * somewhere still has it.
     */
    private static void paragraphs(StringBuilder out, RenderRequest.RenderableSection section) {
        List<RichContent> all = new ArrayList<>(section.atoms());
        for (RenderRequest.RenderableEntry entry : section.entries()) {
            all.addAll(entry.atoms());
        }
        if (all.isEmpty()) {
            return;
        }
        out.append("\\resumeParagraphListStart\n");
        for (RichContent paragraph : all) {
            out.append("\\resumeItem{")
                    .append(LatexInlineRenderer.render(paragraph)).append("}\n");
        }
        out.append("\\resumeParagraphListEnd\n");
    }

    /** One inline block, the way a skills matrix is written (Bolum 33.4). */
    private static void inlineList(StringBuilder out, RenderRequest.RenderableSection section) {
        List<RichContent> all = new ArrayList<>(section.atoms());
        for (RenderRequest.RenderableEntry entry : section.entries()) {
            all.addAll(entry.atoms());
        }
        if (all.isEmpty()) {
            return;
        }
        out.append("\\resumeInlineList{\n");
        for (int index = 0; index < all.size(); index++) {
            out.append(InlineRow.render(all.get(index)));
            // A break between rows and none after the last: a trailing \\
            // inside an \item opens a row that nothing fills.
            out.append(index < all.size() - 1 ? " \\\\\n" : "\n");
        }
        out.append("}\n");
    }

    /**
     * Bullets, through the template's own commands.
     *
     * <p>Used for a section's loose atoms as well as an entry's. A second
     * spelling — a bare {@code itemize} with a bare {@code \item} — printed the
     * same thing under the classic template and would stop doing so the moment
     * a template gave {@code \resumeItem} anything of its own, silently and
     * only for the sections whose atoms hang off the section.
     */
    private static void items(StringBuilder out, List<RichContent> atoms) {
        if (atoms.isEmpty()) {
            return;
        }
        out.append("\\resumeItemListStart\n");
        for (RichContent atom : atoms) {
            out.append("\\resumeItem{").append(LatexInlineRenderer.render(atom)).append("}\n");
        }
        out.append("\\resumeItemListEnd\n");
    }
}
