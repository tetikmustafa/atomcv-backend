package com.mustafatetik.atomcv.generation.phases.analysis;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * <strong>F-025 — the employer is a name the posting carries, or it is
 * nothing.</strong>
 *
 * <p>{@code company.name} is required by the schema and typed as a plain
 * string, so a model with no employer to name still has to write one. The
 * three recorded analyses in this repository write three different things for
 * it — {@code "Unknown"}, the empty string, and a real name — and the frontend
 * measured a fourth, {@code "not specified"}. The empty one is already handled
 * on the wire; the others reach a history row as a label that looks like a
 * fact, which is the thing Bolum 57.6 lets the field exist to avoid.
 *
 * <p><strong>Not a list of placeholder phrases.</strong> That was the obvious
 * fix and it is the wrong one: it would be a guess about which sentences this
 * model writes in which language, and the seventh one would arrive unlisted.
 * What can be checked instead is the property that actually distinguishes a
 * name from an apology for not having one — <em>the posting contains it</em>.
 * "not specified" is not in the text; "Calico Teknoloji" is. The rule is
 * closed, needs no vocabulary, and holds in any language.
 *
 * <p><strong>It fails towards silence.</strong> A model that renames the
 * employer rather than quoting it — an expansion, a translation — loses the
 * label. That is the cheaper of the two mistakes by a distance: a row with no
 * company still says what the job was, and a row naming the wrong one is a
 * lie the reader has no way to spot.
 *
 * <p>The prompt should say this too, and it does not yet. Saying it means a
 * new prompt version (Bolum 53.2), which invalidates every recorded fixture
 * and a week of cache — worth doing when {@code job_analysis} is next
 * versioned for the model that is still being chosen, and not worth doing for
 * a defect that can be closed here deterministically.
 */
final class EmployerName {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private EmployerName() {
    }

    /**
     * The analysis, with an employer the posting does not name removed.
     *
     * @param jobDescription the text the analysis was made from — the only
     *                       evidence there is about what the posting said
     */
    static JobAnalysis verifiedAgainst(JobAnalysis analysis, String jobDescription) {
        String name = analysis.company().name();
        if (name.isBlank() || carriedBy(jobDescription, name)) {
            return analysis;
        }
        return analysis.withoutCompanyName();
    }

    /**
     * Case-insensitive and blind to how the text wrapped: a posting that broke
     * "Calico Teknoloji" across two lines still names it, and a model that
     * writes it in title case still quoted it.
     *
     * <p>{@code Locale.ROOT} on both sides (absolute rule 7). The two strings
     * are folded the same way, so a Turkish dotted capital lands identically
     * in both and the comparison stays a comparison.
     */
    private static boolean carriedBy(String jobDescription, String name) {
        return folded(jobDescription).contains(folded(name));
    }

    private static String folded(String value) {
        return WHITESPACE.matcher(value).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
