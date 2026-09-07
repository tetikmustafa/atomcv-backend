package com.mustafatetik.atomcv.rendering.template;

import java.util.Map;
import java.util.Set;

/**
 * The templates that exist, and the LaTeX each one adds to a preamble
 * (Bolum 33.5).
 *
 * <p>A template id from a request is looked up here and nowhere else: an id
 * that is not in this map is not a template, so no request can name one into
 * existence.
 */
public final class TemplateRegistry {

    /**
     * Raise a version when the geometry changes — spacing, rules, indents.
     *
     * <p>Measured costs are keyed by it (Bolum 16.3). Forgetting to raise it
     * leaves old measurements looking valid for a document that no longer
     * matches them, and the page guarantee fails quietly rather than loudly.
     */
    private static final Map<String, Integer> VERSIONS = Map.of("classic", 4);

    /**
     * Classic (Bolum 33.5): plain, ATS-safe, academic or corporate.
     *
     * <p><strong>This is the reference CV's own preamble, ported.</strong> Not
     * a template in its spirit — its commands, its spacing and its type, to the
     * point. An earlier pass took the commands and left the rest, on the
     * reasoning that the negative spacing could not be costed and that one
     * element in a smaller size would break a capacity model carrying one
     * baseline. Both were true of the model as it stood; the model was what had
     * to move, because a CV that is nearly the reference is a different CV.
     *
     * <p>Three things could not be ported and none of them is visible:
     * {@code \\input{glyphtounicode}} and {@code \\pdfgentounicode=1} are pdfTeX
     * primitives and this compiles with XeLaTeX, which writes a ToUnicode map
     * for every embedded font by itself — the ATS-parsable output they exist
     * for is what we already get. {@code fullpage} plus four
     * {@code \\addtolength}s is arithmetic that lands on half an inch all round
     * (see {@link TemplateCustomization#CLASSIC}), so {@code geometry} does it
     * in one line and leaves the margin a slider. And {@code fancyhdr} with
     * every field cleared is {@code \\pagestyle{empty}}.
     *
     * <p>No two-column layout and no graphics: an ATS extracts text, and a
     * layout that reads well to a person but scrambles under extraction is a
     * CV that never reaches one.
     */
    private static final String CLASSIC_BASE = """
            \\usepackage{titlesec}
            \\usepackage{enumitem}
            \\usepackage[hidelinks]{hyperref}
            \\usepackage{tabularx}
            \\pagestyle{empty}
            \\raggedbottom
            \\raggedright
            \\setlength{\\tabcolsep}{0in}
            \\urlstyle{same}
            % The reference's own section rule, negative leading and all. The
            % -10pt is what pulls a heading up against the block above it and
            % the -3pt closes the gap under the rule; together they are most of
            % why the page reads as tight as it does. Both live inside the
            % format, so both are inside what a section heading costs and the
            % calibration measures them without knowing they are there.
            \\titleformat{\\section}{%
              \\vspace{-10pt}\\raggedright\\large\\bfseries\\color{accent}%
            }{}{0em}{}[\\color{accent}\\titlerule \\vspace{-3pt}]
            % \\small on the bullet, which is the reference's own choice and the
            % one this template used to refuse. A document then has two
            % baselines rather than one -- the page's, and the bullets' -- and
            % the capacity model carries both. Reading it back as a single
            % number is what put every prediction 24-43% over what the page held.
            \\newcommand{\\resumeItem}[1]{%
              \\item\\small{
                {#1 \\vspace{-4pt}}
              }
            }
            \\newcommand{\\resumeSubheading}[4]{%
              \\vspace{-2pt}\\item
                \\begin{tabular*}{0.97\\textwidth}[t]{l@{\\extracolsep{\\fill}}r}
                  \\textbf{#1} & #2 \\\\
                  \\textit{\\small#3} & \\textit{\\small #4} \\\\
                \\end{tabular*}\\vspace{-7pt}%
            }
            \\newcommand{\\resumeProjectHeading}[2]{%
                \\item
                \\begin{tabularx}{0.97\\textwidth}{X r}
                  \\textbf{#1} & #2 \\\\
                \\end{tabularx}\\vspace{-5pt}%
            }
            \\renewcommand\\labelitemii{$\\vcenter{\\hbox{\\tiny$\\bullet$}}$}
            \\newcommand{\\resumeSubHeadingListStart}%
              {\\begin{itemize}[leftmargin=0.15in, label={}]}
            \\newcommand{\\resumeSubHeadingListEnd}{\\end{itemize}}
            \\newcommand{\\resumeItemListStart}{\\begin{itemize}}
            \\newcommand{\\resumeItemListEnd}{\\end{itemize}\\vspace{-5pt}}
            % A summary and a skills matrix are the same list the reference sets
            % its entries in: label-less, at the same indent. It writes its
            % About with \\resumeSubHeadingListStart and its Tech Stack with the
            % same itemize spelled out, so these are that list under names that
            % say which of the two is being opened (Bolum 33.4).
            \\newcommand{\\resumeParagraphListStart}%
              {\\begin{itemize}[leftmargin=0.15in, label={}]}
            \\newcommand{\\resumeParagraphListEnd}{\\end{itemize}}
            \\newcommand{\\resumeInlineList}[1]%
              {\\begin{itemize}[leftmargin=0.15in, label={}]%
                \\small{\\item{#1}}\\end{itemize}}
            % The heading block, as one centred group: the name, ten points, and
            % the contact material set small. Two groups would leave a paragraph
            % skip between them that the reference does not have.
            \\newcommand{\\atomcvHeader}[2]{%
              \\begin{center}
                \\textbf{\\Huge #1} \\\\ \\vspace{10pt}
                \\small #2
              \\end{center}
            }
            """;

    /**
     * Classic at its default customization, measured against the compiler
     * rather than estimated (Bolum 26.4).
     *
     * <p>These hold for {@link TemplateCustomization#CLASSIC} only. Font size,
     * family, margin and line spacing all move them, which is exactly why
     * Bolum 33.1 calls those "layer B": changing one costs a measurement.
     *
     * <p>{@code LatexCalibrationIT} re-derives every number here from a real
     * compilation. When the template's geometry changes, that test fails —
     * which is the moment the version above has to be raised, before a stored
     * cost becomes a quiet lie.
     */
    private static final CapacityModel CLASSIC_CAPACITY = new CapacityModel(
            // The text block at half an inch all round, which is where the
            // reference's fullpage-plus-addtolength arithmetic lands.
            722.7,
            542.02501,
            // Two baselines: the page's, and the one inside a \small bullet.
            13.6,
            12.0,
            Map.ofEntries(
                    // The name, ten points, and two centred lines of contact
                    // material under it, all in one centred group.
                    Map.entry(CapacityModel.HEADER_BLOCK, 63.37671),
                    // The rule, with the negative space the reference writes
                    // above it and below it.
                    Map.entry(CapacityModel.SECTION_HEADER, 20.86289),
                    // Two lines: the title, and the organization with its
                    // dates. A hand-written probe that lost the line break
                    // measured 10.87 and looked entirely plausible - the
                    // calibration test is what caught it (EK D.8.3).
                    Map.entry(CapacityModel.ENTRY_HEADER, 30.19998),
                    Map.entry(CapacityModel.ENTRY_HEADER_AFTER_LIST, 31.17004),
                    // A project's heading is one line, not two.
                    Map.entry(CapacityModel.PROJECT_HEADING, 20.59749),
                    Map.entry(CapacityModel.PROJECT_HEADING_AFTER_LIST, 21.55001),
                    Map.entry(CapacityModel.ITEMIZE_OVERHEAD, 0.54999),
                    // A bullet nested under an entry is a bare small baseline;
                    // one in a list of its own pays the separation a first-level
                    // itemize sets between two items.
                    Map.entry(CapacityModel.ITEM_LINE, 12.0),
                    Map.entry(CapacityModel.SECTION_ITEM_LINE, 17.0),
                    // Three label-less lists, three numbers: a bullet list
                    // closes by pulling five points back, a paragraph list does
                    // not, and an inline row is not wrapped in the four points a
                    // bullet pulls back after itself.
                    Map.entry(CapacityModel.SECTION_LIST_OVERHEAD, -6.95002),
                    Map.entry(CapacityModel.SECTION_LIST_CLOSE, 12.0),
                    Map.entry(CapacityModel.PARAGRAPH_LIST_OVERHEAD, -1.95001),
                    Map.entry(CapacityModel.INLINE_ROW, 12.0),
                    Map.entry(CapacityModel.INLINE_LIST_OVERHEAD, 7.04999)));

    private TemplateRegistry() {
    }

    /**
     * The capacity model for a customization, if one has been measured.
     *
     * <p>Bolum 22.2 returns a model unconditionally. It cannot: a customization
     * nobody has measured has no capacity, and inventing one would break the
     * page guarantee silently — which is the one failure this whole system
     * exists to prevent. Empty means "measure first" (EK D.8.3).
     */
    public static java.util.Optional<CapacityModel> capacityOf(
            TemplateCustomization customization) {

        return TemplateCustomization.CLASSIC.equals(customization)
                ? java.util.Optional.of(CLASSIC_CAPACITY)
                : java.util.Optional.empty();
    }

    public static Set<String> ids() {
        return VERSIONS.keySet();
    }

    public static boolean exists(String templateId) {
        return VERSIONS.containsKey(templateId);
    }

    public static int versionOf(String templateId) {
        Integer version = VERSIONS.get(templateId);
        if (version == null) {
            throw new IllegalArgumentException("No such template");
        }
        return version;
    }

    /** The template's own preamble lines, appended after the shared ones. */
    public static String baseOf(String templateId) {
        if (!"classic".equals(templateId)) {
            throw new IllegalArgumentException("No such template");
        }
        return CLASSIC_BASE;
    }
}
