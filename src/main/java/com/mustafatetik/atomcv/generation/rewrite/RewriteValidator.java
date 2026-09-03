package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.shared.math.Vectors;
import com.mustafatetik.atomcv.shared.text.ClaimVocabulary;
import com.mustafatetik.atomcv.shared.text.SkillNames;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bolum 21.6 — nothing is printed that has not been checked.
 *
 * <p>Five checks, and they are not equally important. Four of them catch a
 * model doing its job badly; the third catches it doing the thing this product
 * exists to prevent. A CV that claims Kubernetes because the posting asked for
 * Kubernetes is not a worse CV, it is a false one, and the person finds out in
 * an interview.
 *
 * <p>Pure and static: text, the atom's constraints and two vectors go in, a
 * list of issues comes out. Nothing here decides what to do about them —
 * Bolum 21.6's answer to any issue at all is to try once more and then print
 * the original, and that belongs with the caller that can do it.
 */
public final class RewriteValidator {

    /** Bolum 21.6, verbatim: below this it no longer says what it said. */
    static final double MIN_SIMILARITY = 0.80;

    /** A run of digits, whatever surrounds it. Bolum 21.8's rule, for the same reason. */
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private RewriteValidator() {
    }

    /**
     * @param candidate     what was asked for, carrying everything the answer
     *                      is allowed to claim
     * @param rewritten     the answer
     * @param postingSkills what the posting asked for, canonical. The
     *                      vocabulary a keyword-stuffed answer would draw from
     * @param rewrittenVector the answer's embedding, or {@code null} when the
     *                      embedding service could not be reached
     * @param originalVector the atom's own, or {@code null} when it has none
     *                      yet
     */
    public static List<RewriteIssue> validate(
            RewriteCandidate candidate,
            String rewritten,
            List<String> postingSkills,
            float[] rewrittenVector,
            float[] originalVector) {

        List<RewriteIssue> issues = new ArrayList<>();
        String answer = rewritten == null ? "" : rewritten;
        String folded = answer.toLowerCase(Locale.ROOT);

        // 1 and 2. Bolum 21.8's rules, applied to a rewrite for the same
        // reason: a sentence that still reads perfectly and has lost a number
        // is the alteration nobody proofreads out.
        List<String> digitsPresent = digitsOf(answer);
        for (String metric : candidate.metrics()) {
            if (!digitsOf(metric).stream().allMatch(digitsPresent::contains)) {
                issues.add(RewriteIssue.NUMBER_LOST);
                break;
            }
        }
        for (String noun : candidate.properNouns()) {
            if (noun != null && !noun.isBlank()
                    && !folded.contains(noun.toLowerCase(Locale.ROOT))) {
                issues.add(RewriteIssue.PROPER_NOUN_LOST);
                break;
            }
        }

        // 3. The one that matters.
        if (claimsSomethingItCannot(candidate, answer, folded, postingSkills)) {
            issues.add(RewriteIssue.UNSUPPORTED_CLAIM);
        }

        // 4. Bolum 21.3's ceiling, checked and not merely requested.
        if (answer.length() > candidate.maxChars()) {
            issues.add(RewriteIssue.TOO_LONG);
        }

        // 5. Whether it still says what it said.
        if (rewrittenVector != null && originalVector != null
                && Vectors.cosine(rewrittenVector, originalVector) < MIN_SIMILARITY) {
            issues.add(RewriteIssue.SEMANTIC_DRIFT);
        }
        return List.copyOf(issues);
    }

    /**
     * Bolum 21.6's third check, against the vocabulary a stuffed answer would
     * draw from: what the posting asked for, plus the names the alias
     * dictionary knows.
     *
     * <p><strong>Ekleme — a technology already in the original does not count
     * against the rewrite.</strong> Bolum 21.6 compares what the answer
     * mentions against {@code atom.skills} and nothing else, which fails every
     * rewrite of an atom whose skills were extracted incompletely: the person
     * wrote "Postgres" in their own bullet, the extraction did not list it,
     * and the model is now rejected for keeping the word it was told to keep.
     * The check is about claims the rewrite <em>introduced</em>, and that is
     * the difference between a guard and an outage.
     */
    private static boolean claimsSomethingItCannot(
            RewriteCandidate candidate, String answer, String foldedAnswer,
            List<String> postingSkills) {

        String foldedOriginal = candidate.originalText().toLowerCase(Locale.ROOT);
        Set<String> allowed = new LinkedHashSet<>(candidate.skills());

        for (String term : ClaimVocabulary.of(postingSkills)) {
            if (!ClaimVocabulary.mentions(foldedAnswer, term)) {
                continue;
            }
            if (allowed.contains(SkillNames.canonical(term))) {
                continue;
            }
            if (ClaimVocabulary.mentions(foldedOriginal, term)) {
                // The person's own word. Not this phase's argument to have.
                continue;
            }
            return true;
        }
        // And the other half of the question: a name none of the sources
        // carry. The loop above can only refuse what the alias file knows.
        return !ClaimVocabulary.introducedNames(answer, sourcesOf(candidate, postingSkills))
                .isEmpty();
    }

    /** Everything the rewrite was allowed to draw a name from. */
    private static List<String> sourcesOf(
            RewriteCandidate candidate, List<String> postingSkills) {

        List<String> sources = new ArrayList<>();
        sources.add(candidate.originalText());
        sources.addAll(candidate.skills());
        sources.addAll(postingSkills);
        return sources;
    }

    private static List<String> digitsOf(String value) {
        if (value == null) {
            return List.of();
        }
        return DIGITS.matcher(value).results().map(match -> match.group()).toList();
    }
}
