package com.mustafatetik.atomcv.generation.scoring;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Faz B: how well each atom answers this posting (Bolum 19).
 *
 * <p><strong>There is no threshold.</strong> Bolum 19.3 is emphatic and it is
 * the reason the product never says "nothing relevant found": someone applying
 * outside their field still gets a full page, their scores are simply low in
 * absolute terms — and Faz F says so honestly rather than the page being
 * empty.
 *
 * <p>Pure and deterministic. No clock, no database, no session: Bolum 51.2's
 * determinism test compares two runs over the same inputs, and a scorer that
 * reached for any of those could not pass it.
 *
 * <p>The embedding similarity is computed here rather than through pgvector's
 * {@code <=>}. Bolum 19.2's snippet implies a query, but the profile tree is
 * already in memory by the time Faz B runs — a query would add a round trip to
 * fetch what is loaded, and it would move the ranking into SQL where the
 * determinism test cannot reach it.
 */
public final class RelevanceScorer {

    private static final Pattern TOKENS = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Bolum 19.2: a preferred skill counts, but not as much as a required one. */
    private static final double PREFERRED_SKILL_WEIGHT = 0.4;

    private RelevanceScorer() {
    }

    /**
     * Every atom, ranked (Bolum 19.6).
     *
     * <p>Ties break on the atom id as a string, which Bolum 19.6 calls
     * mandatory. Without it two atoms of equal score swap places between runs
     * and the same input produces a different CV.
     */
    public static List<ScoredAtom> rank(
            List<ScorableAtom> atoms, JobAnalysis posting, float[] postingVector,
            ScoringWeights weights) {

        var target = new PostingTarget(posting);
        return atoms.stream()
                .map(atom -> score(atom, target, postingVector, weights))
                .sorted(ScoredAtom.MOST_RELEVANT_FIRST)
                .toList();
    }

    static ScoredAtom score(
            ScorableAtom atom, PostingTarget target, float[] postingVector,
            ScoringWeights weights) {

        double embedding = weights.usesEmbedding()
                ? cosineSimilarity(atom.embedding(), postingVector)
                : 0.0;
        double tag = jaccard(atom.tags(), target.tags());
        double skill = skillOverlap(atom.skills(), target);
        double keyword = coverage(atom.tags(), target.keywords());

        double raw = weights.embedding() * embedding
                + weights.tag() * tag
                + weights.skill() * skill
                + weights.keyword() * keyword;

        // Bolum 19.1: importance in [0,1] becomes a multiplier in [0.5, 1.5].
        // A raw score can therefore exceed one, which is why the final score
        // is clamped rather than assumed to be a fraction.
        double finalScore = clamp(raw * (0.5 + atom.importance()));
        return new ScoredAtom(atom.atomId(), finalScore,
                new ScoredAtom.Components(embedding, tag, skill, keyword));
    }

    /**
     * Cosine similarity, mapped into [0,1].
     *
     * <p>Raw cosine runs [-1,1] and a negative component would subtract from
     * the weighted sum, letting an unrelated bullet score below an empty one.
     * Rescaled, "unrelated" is 0.5 and "opposite" is 0 — which is what the
     * weights were chosen against.
     *
     * @return {@code 0.5}, the neutral value, when either side has no vector.
     *         Bolum 28.2 embeds on a queue after the fact, so an atom written a
     *         moment ago has none — and treating that as "maximally unrelated"
     *         would bury exactly the content the user just decided mattered.
     */
    static double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length != right.length) {
            return 0.5;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += (double) left[index] * right[index];
            leftNorm += (double) left[index] * left[index];
            rightNorm += (double) right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0.5;
        }
        return clamp((dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)) + 1.0) / 2.0);
    }

    /** Bolum 19.2: the profile's vocabulary against the posting's. */
    static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        var intersection = new HashSet<>(left);
        intersection.retainAll(right);
        var union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    /**
     * Bolum 19.2, weighted by what the posting asked for.
     *
     * <p>The denominator is the required list, not the union: a posting with
     * two requirements and twelve nice-to-haves should let an atom covering
     * both requirements score full marks. Preferred skills add on top and the
     * total is clamped, so they can lift an atom that misses a requirement
     * without ever standing in for one.
     */
    static double skillOverlap(Set<String> atomSkills, PostingTarget target) {
        if (atomSkills.isEmpty()) {
            return 0.0;
        }
        double required = target.requiredSkills().isEmpty() ? 0.0
                : (double) countMatches(atomSkills, target.requiredSkills())
                        / target.requiredSkills().size();
        double preferred = target.preferredSkills().isEmpty() ? 0.0
                : PREFERRED_SKILL_WEIGHT
                        * countMatches(atomSkills, target.preferredSkills())
                        / target.preferredSkills().size();
        return clamp(required + preferred);
    }

    /** How much of the posting's vocabulary this atom covers at all. */
    static double coverage(Set<String> atomTags, Set<String> postingKeywords) {
        if (atomTags.isEmpty() || postingKeywords.isEmpty()) {
            return 0.0;
        }
        return (double) countMatches(atomTags, postingKeywords) / postingKeywords.size();
    }

    private static long countMatches(Set<String> left, Set<String> right) {
        return left.stream().filter(right::contains).count();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * The posting reduced to the sets Bolum 19.2 compares against, computed
     * once for the whole ranking rather than per atom.
     */
    record PostingTarget(
            Set<String> tags, Set<String> keywords,
            Set<String> requiredSkills, Set<String> preferredSkills) {

        PostingTarget(JobAnalysis posting) {
            this(tagsOf(posting), canonical(posting.keywords()),
                    canonicalSkills(posting.requiredSkills()),
                    canonicalSkills(posting.preferredSkills()));
        }

        /** Bolum 19.2: the domain, the keywords, and the title's own words. */
        private static Set<String> tagsOf(JobAnalysis posting) {
            var tags = new HashSet<String>();
            tags.addAll(canonical(List.of(posting.role().domain())));
            tags.addAll(canonical(posting.keywords()));
            tags.addAll(tokensOf(posting.role().title()));
            return Set.copyOf(tags);
        }

        private static Set<String> canonicalSkills(List<JobAnalysis.Skill> skills) {
            return skills.stream()
                    .map(JobAnalysis.Skill::canonical)
                    .filter(name -> !name.isBlank())
                    // Locale.ROOT: absolute rule 7. A Turkish locale turns
                    // "SQL" into "sqı" and no atom would ever match it.
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        private static Set<String> canonical(List<String> values) {
            return values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        private static Set<String> tokensOf(String title) {
            return TOKENS.splitAsStream(title)
                    .filter(token -> !token.isBlank())
                    .map(token -> token.toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    /** For tests and callers that hold a posting but no vector yet. */
    public static List<ScoredAtom> rank(
            List<ScorableAtom> atoms, JobAnalysis posting, ScoringWeights weights) {
        return rank(atoms, posting, null, weights);
    }
}
