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

    private TemplateRegistry() {
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
