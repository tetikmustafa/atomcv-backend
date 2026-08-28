package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.shared.text.ClaimVocabulary;
import com.mustafatetik.atomcv.shared.text.SkillNames;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bolum 21.7's check: a summary claims nothing the page does not already say.
 *
 * <p>Three rules where Bolum 21.6 has five, and the two that are missing are
 * missing on purpose. <strong>Nothing has to survive</strong> — a synthesis is
 * not a rewrite of one sentence, so there is no number or name it was asked to
 * keep. And <strong>there is no drift check</strong>: the paragraph is
 * deliberately not what it was, and measuring it against the old one would be
 * a rule that fails exactly when the phase worked.
 *
 * <p>What is left is stricter than Bolum 21.6 in the way that matters. A
 * rewritten bullet is checked against its own atom's skills; this is checked
 * against the skills of the whole page, and a summary is where a model most
 * wants to round up — three years here and four there into "a decade", two
 * databases into "extensive data infrastructure experience".
 */
public final class AboutValidator {

    /** A run of digits, whatever surrounds it. Bolum 21.6's rule 1, reversed. */
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private AboutValidator() {
    }

    /**
     * @param candidate     the page's own skills and numbers
     * @param synthesised   the answer
     * @param postingSkills what this posting asked for — not permission to
     *                      claim any of it, but the vocabulary a stuffed
     *                      summary would draw from
     */
    public static List<RewriteIssue> validate(
            AboutCandidate candidate, String synthesised, List<String> postingSkills) {
        List<RewriteIssue> issues = new ArrayList<>();
        String answer = synthesised == null ? "" : synthesised;
        String folded = answer.toLowerCase(Locale.ROOT);

        if (answer.isBlank()) {
            // An empty summary is not a shorter summary; it is a heading with
            // nothing under it, which selection already paid for.
            issues.add(RewriteIssue.TOO_LONG);
            return List.copyOf(issues);
        }

        // 1. Bolum 21.7, verbatim: every technology is one the page carries.
        if (namesSomethingThePageDoesNot(candidate, folded, postingSkills)) {
            issues.add(RewriteIssue.UNSUPPORTED_CLAIM);
        }

        // 2. And every number. A summary is where two figures become a third.
        if (carriesANumberThePageDoesNot(candidate, answer)) {
            issues.add(RewriteIssue.NUMBER_INVENTED);
        }

        // 3. Bolum 21.3's ceiling, narrowed by Bolum 21.7's ~65 words.
        if (answer.length() > candidate.maxChars()) {
            issues.add(RewriteIssue.TOO_LONG);
        }
        return List.copyOf(issues);
    }

    /**
     * The vocabulary is the same one Bolum 21.6 checks a bullet against — the
     * alias dictionary is the only list of technology names this codebase has
     * — but the permitted half is the page's skills rather than one atom's.
     *
     * <p>The person's own paragraph is a second source of permission, for the
     * reason Bolum 21.6.1 gives: a skill they wrote about themselves and the
     * extraction never listed is theirs to claim, and refusing it would be an
     * outage rather than a guard.
     */
    private static boolean namesSomethingThePageDoesNot(
            AboutCandidate candidate, String foldedAnswer, List<String> postingSkills) {

        Set<String> allowed = new LinkedHashSet<>();
        for (String skill : candidate.skills()) {
            allowed.add(SkillNames.canonical(skill));
        }
        String foldedOwn = (candidate.originalText() + " " + candidate.ownWords())
                .toLowerCase(Locale.ROOT);

        for (String term : ClaimVocabulary.of(postingSkills)) {
            if (!ClaimVocabulary.mentions(foldedAnswer, term)) {
                continue;
            }
            if (allowed.contains(SkillNames.canonical(term)) || ClaimVocabulary.mentions(foldedOwn, term)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Every digit run in the answer appears somewhere the page already says
     * it: in a selected atom's metrics, or in what the person wrote about
     * themselves. "Eight years" is a claim, and a summary is not where it is
     * made for the first time.
     */
    private static boolean carriesANumberThePageDoesNot(
            AboutCandidate candidate, String answer) {

        Set<String> known = new LinkedHashSet<>();
        for (String metric : candidate.metrics()) {
            known.addAll(digitsOf(metric));
        }
        known.addAll(digitsOf(candidate.ownWords()));
        known.addAll(digitsOf(candidate.originalText()));
        return !known.containsAll(digitsOf(answer));
    }

    private static List<String> digitsOf(String value) {
        if (value == null) {
            return List.of();
        }
        return DIGITS.matcher(value).results().map(match -> match.group()).toList();
    }
}
