package com.mustafatetik.atomcv.profile.domain.content;

import java.util.Locale;

/**
 * What may be said about an atom's wording in a log line.
 *
 * <p>Absolute rule 4 forbids logging the content itself, which leaves a real
 * gap: a rewrite refused for {@code TOO_LONG} and a wording that would not
 * measure are both reported today as a rule name and an id, and neither says
 * anything a person could act on. This is the statistics half of that rule —
 * enough shape to tell an ordinary bullet from a pathological one without
 * carrying a single word of it.
 *
 * <p><strong>{@code ExtractedText.shape()} objected to reusing this, and it
 * was right.</strong> Its fields are an atom's — runs, emphasis, render cost —
 * and an extracted file has none of them, so a shared record would have been
 * half zeroes at every call site. That objection is about the extraction
 * stage, not about the record: here every field is a fact about the thing
 * being described.
 *
 * <p><strong>Nothing here can be turned back into the sentence.</strong> Every
 * field is a count, a flag or a language tag. That is the property worth
 * keeping when a field is added: "how long", "how many numbers" and "does it
 * contain a backslash" are shape; a first word, a longest token or a hash of
 * the text are not.
 *
 * @param charCount        characters of plain text
 * @param wordCount        whitespace-separated tokens
 * @param runCount         runs in the rich content
 * @param emphasisCount    runs carrying at least one mark, whatever the mark:
 *                         three of the five print bold, and what matters in a
 *                         diagnostic is how much of the line is marked at all
 * @param numericTokenCount tokens containing a digit, which is the shape a
 *                         metric has and the reason a rewrite gets refused
 * @param properNounCount  names the atom claims, passed in rather than
 *                         guessed: capitalisation is not a reliable test in
 *                         any language and is a worse one in Turkish
 * @param language         BCP 47 tag of the wording
 * @param hasNonAscii      anything above U+007F, which is the first thing to
 *                         look at when a document compiles here and not there
 * @param hasSpecialLatex  a character the LaTeX escape has to handle
 * @param renderCostPt     the measured height, or {@code 0} when the point of
 *                         the log line is that there is no measurement
 */
public record ContentShape(
        int charCount,
        int wordCount,
        int runCount,
        int emphasisCount,
        int numericTokenCount,
        int properNounCount,
        String language,
        boolean hasNonAscii,
        boolean hasSpecialLatex,
        double renderCostPt) {

    /** The characters {@code LatexInlineRenderer} has to escape. */
    private static final String LATEX_SPECIALS = "&%$#_{}~^\\";

    public ContentShape {
        language = language == null ? "" : language;
    }

    /** The shape of a wording whose height has been measured. */
    public static ContentShape of(RichContent content, java.util.List<String> properNouns,
            String language, double renderCostPt) {

        String text = content.plainText();
        return new ContentShape(
                text.length(),
                wordCount(text),
                content.runs().size(),
                (int) content.runs().stream().filter(run -> !run.marks().isEmpty()).count(),
                numericTokens(text),
                properNouns == null ? 0 : properNouns.size(),
                language,
                hasNonAscii(text),
                hasSpecialLatex(text),
                renderCostPt);
    }

    /**
     * The shape of a wording with no measurement to report.
     *
     * <p>Its own factory rather than a zero passed by hand: the two call sites
     * that need it are both about a measurement that did not happen, and a
     * literal zero at a call site reads as a cost of nothing.
     */
    public static ContentShape unmeasured(RichContent content,
            java.util.List<String> properNouns, String language) {

        return of(content, properNouns, language, 0);
    }

    private static int wordCount(String text) {
        if (text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private static int numericTokens(String text) {
        if (text.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String token : text.trim().split("\\s+")) {
            if (token.chars().anyMatch(Character::isDigit)) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasNonAscii(String text) {
        return text.chars().anyMatch(c -> c > 0x7F);
    }

    private static boolean hasSpecialLatex(String text) {
        return text.chars().anyMatch(c -> LATEX_SPECIALS.indexOf(c) >= 0);
    }

    /**
     * One log-safe line.
     *
     * <p>{@code Locale.ROOT} on the cost: absolute rule 7 reaches formatting
     * too, and a Turkish default turns {@code 41.2} into {@code 41,2} — which
     * is a different number to anything parsing the log.
     */
    @Override
    public String toString() {
        var line = new StringBuilder("chars=").append(charCount)
                .append(" words=").append(wordCount)
                .append(" runs=").append(runCount)
                .append(" marked=").append(emphasisCount)
                .append(" numeric=").append(numericTokenCount)
                .append(" names=").append(properNounCount)
                .append(" lang=").append(language)
                .append(" nonAscii=").append(hasNonAscii)
                .append(" latexSpecial=").append(hasSpecialLatex);
        if (renderCostPt > 0) {
            line.append(String.format(Locale.ROOT, " costPt=%.1f", renderCostPt));
        }
        return line.toString();
    }
}
