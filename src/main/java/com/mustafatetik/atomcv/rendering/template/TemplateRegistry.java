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
    private static final Map<String, Integer> VERSIONS = Map.of("classic", 1);

    /**
     * Classic (Bolum 33.5): plain, ATS-safe, academic or corporate.
     *
     * <p>No two-column layout and no graphics: an ATS extracts text, and a
     * layout that reads well to a person but scrambles under extraction is a
     * CV that never reaches one.
     */
    private static final String CLASSIC_BASE = """
            \\usepackage{titlesec}
            \\usepackage{enumitem}
            \\usepackage{parskip}
            \\usepackage[hidelinks]{hyperref}
            \\pagestyle{empty}
            \\titleformat{\\section}{\\normalsize\\bfseries\\color{accent}}{}{0em}{}[\\titlerule]
            \\titlespacing*{\\section}{0pt}{8pt}{4pt}
            \\setlist[itemize]{leftmargin=0.15in,label={--},topsep=2pt,itemsep=1pt,parsep=0pt}
            \\newcommand{\\atomcvName}[1]{\\begin{center}{\\LARGE\\bfseries #1}\\end{center}}
            \\newcommand{\\atomcvContact}[1]{\\begin{center}\\small #1\\end{center}}
            \\newcommand{\\atomcvEntry}[4]{%
              \\noindent\\textbf{#1}\\hfill{\\small #3}\\\\%
              {\\small\\itshape #2}\\hfill{\\small #4}\\par}
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
            708.245,
            527.571,
            12.0,
            Map.of(
                    // Name, headline and contact line, as the first thing on
                    // the page. An earlier 52.0 was measured after a \null,
                    // which bought the header a baseline gap no real document
                    // has (EK D.8.10).
                    CapacityModel.HEADER_BLOCK, 45.68127,
                    CapacityModel.SECTION_HEADER, 24.0,
                    CapacityModel.ENTRY_HEADER_AFTER_LIST, 32.0,
                    // Two lines: the title, and the organization with its
                    // dates. A hand-written probe that lost the line break
                    // measured 10.87 and looked entirely plausible — the
                    // calibration test is what caught it (EK D.8.3).
                    CapacityModel.ENTRY_HEADER, 22.76,
                    CapacityModel.ITEMIZE_OVERHEAD, 7.0,
                    CapacityModel.ITEM_LINE, 13.0));

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
