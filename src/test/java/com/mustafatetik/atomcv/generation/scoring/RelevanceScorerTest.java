package com.mustafatetik.atomcv.generation.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** Bolum 19: the ranking, the multiplier, and the two rules that are absolute. */
class RelevanceScorerTest {

    private static final Offset<Double> EPSILON = Offset.offset(1e-9);

    // ── Bolum 19.3: no threshold, ever ───────────────────────────────────

    /**
     * The rule that makes "no relevant content found" impossible. Someone
     * applying outside their field gets a full page with low scores, and
     * Faz F tells them so — an empty page would tell them nothing.
     */
    @Test
    void everyAtomIsRankedEvenWhenNothingMatches() {
        var atoms = List.of(
                atom(Set.of("baking", "pastry"), Set.of("sourdough"), 0.5),
                atom(Set.of("gardening"), Set.of("pruning"), 0.5));

        var ranked = RelevanceScorer.rank(atoms, backendPosting(), ScoringWeights.DEFAULT);

        assertThat(ranked).hasSize(2);
        assertThat(ranked).allSatisfy(scored -> assertThat(scored.score()).isLessThan(0.5));
    }

    // ── Bolum 19.6: determinism ──────────────────────────────────────────

    /**
     * Bolum 19.6 calls the tie-break mandatory, and this is why: without it
     * the same profile and the same posting produce a different CV run to run.
     */
    @Test
    void tiedAtomsComeBackInTheSameOrderWhateverTheInputOrder() {
        var tied = new ArrayList<>(List.of(
                atom(Set.of("go"), Set.of("go"), 0.5),
                atom(Set.of("go"), Set.of("go"), 0.5),
                atom(Set.of("go"), Set.of("go"), 0.5),
                atom(Set.of("go"), Set.of("go"), 0.5)));

        var expected = RelevanceScorer.rank(tied, backendPosting(), ScoringWeights.DEFAULT)
                .stream().map(ScoredAtom::atomId).toList();

        var shuffler = new Random(42);
        for (int attempt = 0; attempt < 20; attempt++) {
            Collections.shuffle(tied, shuffler);
            assertThat(RelevanceScorer.rank(tied, backendPosting(), ScoringWeights.DEFAULT)
                    .stream().map(ScoredAtom::atomId).toList()).isEqualTo(expected);
        }
    }

    @Test
    void thesameInputsProduceTheSameScores() {
        var atoms = List.of(
                atom(Set.of("go", "postgres"), Set.of("go"), 0.8),
                atom(Set.of("kubernetes"), Set.of("kubernetes"), 0.4));

        assertThat(RelevanceScorer.rank(atoms, backendPosting(), ScoringWeights.DEFAULT))
                .isEqualTo(RelevanceScorer.rank(atoms, backendPosting(), ScoringWeights.DEFAULT));
    }

    // ── Bolum 19.1: the importance multiplier ────────────────────────────

    @Test
    void importanceScalesTheScoreBetweenHalfAndOneAndAHalf() {
        var quiet = atom(Set.of("go"), Set.of("go"), 0.0);
        var loud = atom(Set.of("go"), Set.of("go"), 1.0);
        var target = new RelevanceScorer.PostingTarget(backendPosting());

        double quietScore = RelevanceScorer.score(
                quiet, target, null, ScoringWeights.DEFAULT).score();
        double loudScore = RelevanceScorer.score(
                loud, target, null, ScoringWeights.DEFAULT).score();

        assertThat(loudScore / quietScore).isCloseTo(3.0, Offset.offset(1e-6));
    }

    /**
     * The multiplier can push a raw score above one, which is why the result
     * is clamped. Without it a perfectly matching, maximally important atom
     * would fail ScoredAtom's own range check — a defect that appears only for
     * the best possible content.
     */
    @Test
    void aperfectAtomDoesNotScoreAboveOne() {
        var perfect = new ScorableAtom(UUID.randomUUID(), null,
                Set.of("go", "postgres", "fintech", "senior", "backend", "engineer",
                        "distributed systems", "high availability"),
                Set.of("go", "postgres", "terraform"), List.of(), 1.0, 0.5);

        var scored = RelevanceScorer.score(perfect,
                new RelevanceScorer.PostingTarget(backendPosting()), null,
                ScoringWeights.DEFAULT);

        assertThat(scored.score()).isLessThanOrEqualTo(1.0);
    }

    // ── The components ───────────────────────────────────────────────────

    @Test
    void jaccardIsTheOverlapOverTheUnion() {
        assertThat(RelevanceScorer.jaccard(Set.of("a", "b"), Set.of("a", "b")))
                .isCloseTo(1.0, EPSILON);
        assertThat(RelevanceScorer.jaccard(Set.of("a", "b"), Set.of("b", "c")))
                .isCloseTo(1.0 / 3, EPSILON);
        assertThat(RelevanceScorer.jaccard(Set.of("a"), Set.of())).isZero();
    }

    /**
     * The denominator is the required list, not the union: a posting with two
     * requirements and twelve nice-to-haves should let an atom covering both
     * requirements score full marks.
     */
    @Test
    void coveringEveryRequiredSkillIsFullMarksHoweverManyAreMerelyPreferred() {
        var target = new RelevanceScorer.PostingTarget(postingWith(
                List.of("go", "postgres"),
                List.of("terraform", "kafka", "grafana", "aws", "gcp", "azure")));

        assertThat(RelevanceScorer.skillOverlap(Set.of("go", "postgres"), target))
                .isCloseTo(1.0, EPSILON);
    }

    /** A preferred skill lifts an atom, but never stands in for a requirement. */
    @Test
    void preferredSkillsAddOnTopWithoutReplacingARequirement() {
        var target = new RelevanceScorer.PostingTarget(postingWith(
                List.of("go", "postgres"), List.of("terraform")));

        double halfRequired = RelevanceScorer.skillOverlap(Set.of("go"), target);
        double halfPlusPreferred = RelevanceScorer.skillOverlap(Set.of("go", "terraform"), target);

        assertThat(halfRequired).isCloseTo(0.5, EPSILON);
        assertThat(halfPlusPreferred).isCloseTo(0.9, EPSILON);
        assertThat(RelevanceScorer.skillOverlap(Set.of("terraform"), target))
                .isCloseTo(0.4, EPSILON);
    }

    // ── Bolum 19.4: what decides between two close atoms ─────────────────

    /**
     * Relevance dominates. A bucket apart is a bucket apart, whatever the
     * secondary score says — otherwise a recent irrelevant bullet would climb
     * over an older relevant one, which is the failure Bolum 19 exists to
     * prevent.
     */
    @Test
    void abetterRelevanceScoreWinsHoweverPoorTheSecondaryOne() {
        var relevant = scored(0.80, 0.0);
        var recent = scored(0.60, 1.0);

        assertThat(ranked(recent, relevant)).containsExactly(relevant, recent);
    }

    /**
     * Within one bucket, Bolum 19.4 decides: recency, importance, impact,
     * verification. Two atoms this close are not meaningfully different on
     * relevance, and the weights of Bolum 19.1 are tuned to one decimal.
     */
    @Test
    void withinOneBucketTheGeneralModeCriteriaDecide() {
        var older = scored(0.805, 0.2);
        var fresher = scored(0.800, 0.9);

        // Higher raw relevance, but the same bucket — so the fresher one wins.
        assertThat(ranked(older, fresher)).containsExactly(fresher, older);
    }

    /**
     * The reason this is a bucket and not an epsilon.
     *
     * <p>A comparator that asks "are these within 0.02 of each other" is not
     * transitive: with a ≈ b and b ≈ c but a ≢ c, {@code List.sort} detects the
     * inconsistency and throws — on a large profile, in production, having
     * passed every smaller test. A chain of scores 0.02 apart is exactly that
     * shape, and sorting it here must simply work.
     */
    @Test
    void alongChainOfNearlyEqualScoresSortsWithoutComplaint() {
        List<ScoredAtom> chain = new ArrayList<>();
        for (int step = 0; step < 60; step++) {
            chain.add(scored(step * 0.015, 1.0 - step * 0.015));
        }
        Collections.shuffle(chain, new Random(7));

        var sorted = chain.stream().sorted(ScoredAtom.MOST_RELEVANT_FIRST).toList();

        assertThat(sorted).hasSize(60);
        assertThat(sorted.get(0).score()).isGreaterThan(sorted.get(59).score());
    }

    /**
     * Bolum 19.6 is still mandatory, and it is still last. It is also reached
     * far less often now — ids are regenerated on every import, so an ordering
     * that leaned on them changed when the same content was imported twice.
     */
    @Test
    void theidIsTheLastResortAndStillBreaksATrueTie() {
        var first = new ScoredAtom(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                0.5, 0.5, new ScoredAtom.Components(0, 0, 0, 0));
        var second = new ScoredAtom(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                0.5, 0.5, new ScoredAtom.Components(0, 0, 0, 0));

        assertThat(ranked(second, first)).containsExactly(first, second);
    }

    private static List<ScoredAtom> ranked(ScoredAtom... atoms) {
        return java.util.Arrays.stream(atoms).sorted(ScoredAtom.MOST_RELEVANT_FIRST).toList();
    }

    private static ScoredAtom scored(double relevance, double secondary) {
        return new ScoredAtom(UUID.randomUUID(), relevance, secondary,
                new ScoredAtom.Components(0, 0, 0, 0));
    }

    // ── The keyword component ────────────────────────────────────────────

    /**
     * Postings write phrases and bullets write sentences, so a keyword counts
     * when every word of it is present rather than when the whole string
     * matches. Equality would score almost every atom at zero.
     */
    @Test
    void akeywordCountsWhenEveryWordOfItIsPresent() {
        List<String> tokens = List.of("built", "a", "distributed", "queue", "for", "systems");

        assertThat(RelevanceScorer.keywordCoverage(
                tokens, Set.of("distributed systems"))).isCloseTo(1.0, EPSILON);
        assertThat(RelevanceScorer.keywordCoverage(
                tokens, Set.of("distributed systems", "high availability")))
                .isCloseTo(0.5, EPSILON);
    }

    /** Half a phrase is not the phrase. */
    @Test
    void onewordOfATwoWordKeywordDoesNotCoverIt() {
        assertThat(RelevanceScorer.keywordCoverage(
                List.of("ran", "many", "systems"), Set.of("distributed systems"))).isZero();
    }

    @Test
    void anatomWithNoWordsAndAPostingWithNoKeywordsBothScoreZero() {
        assertThat(RelevanceScorer.keywordCoverage(List.of(), Set.of("go"))).isZero();
        assertThat(RelevanceScorer.keywordCoverage(List.of("go"), Set.of())).isZero();
    }

    /**
     * The reason this component reads {@code contentTokens} and not
     * {@code tags}: the tag term already compares tags against a set that
     * contains the posting's keywords, so reading tags here counted one signal
     * twice and never looked at what the atom says.
     */
    @Test
    void thekeywordComponentReadsTheAtomsWordsRatherThanItsTags() {
        var target = new RelevanceScorer.PostingTarget(backendPosting());
        var tagged = new ScorableAtom(UUID.randomUUID(), null,
                Set.of("distributed systems", "high availability"), Set.of(), List.of(), 0.5, 0.5);
        var spoken = new ScorableAtom(UUID.randomUUID(), null, Set.of(), Set.of(),
                List.of("kept", "distributed", "systems", "at", "high", "availability"), 0.5, 0.5);

        assertThat(RelevanceScorer.score(tagged, target, null, ScoringWeights.DEFAULT)
                .components().keyword()).isZero();
        assertThat(RelevanceScorer.score(spoken, target, null, ScoringWeights.DEFAULT)
                .components().keyword()).isCloseTo(1.0, EPSILON);
    }

    // ── Embedding similarity ─────────────────────────────────────────────

    @Test
    void identicalVectorsScoreOneAndOppositeOnesZero() {
        var vector = new float[] {1f, 0f, 0f};

        assertThat(RelevanceScorer.cosineSimilarity(vector, vector)).isCloseTo(1.0, EPSILON);
        assertThat(RelevanceScorer.cosineSimilarity(vector, new float[] {-1f, 0f, 0f}))
                .isCloseTo(0.0, EPSILON);
        assertThat(RelevanceScorer.cosineSimilarity(vector, new float[] {0f, 1f, 0f}))
                .isCloseTo(0.5, EPSILON);
    }

    /**
     * Bolum 28.2 embeds on a queue after the fact, so an atom written a moment
     * ago has no vector. Scoring that as maximally unrelated would bury
     * exactly the content the user just decided mattered.
     */
    @Test
    void anAtomWithNoVectorIsNeutralRatherThanUnrelated() {
        assertThat(RelevanceScorer.cosineSimilarity(null, new float[] {1f, 0f}))
                .isCloseTo(0.5, EPSILON);
        assertThat(RelevanceScorer.cosineSimilarity(new float[] {1f, 0f}, null))
                .isCloseTo(0.5, EPSILON);
    }

    /** A vector from another model is not comparable, and pretending is worse. */
    @Test
    void vectorsOfDifferentLengthsAreNeutralRatherThanCompared() {
        assertThat(RelevanceScorer.cosineSimilarity(new float[] {1f, 0f}, new float[] {1f}))
                .isCloseTo(0.5, EPSILON);
    }

    // ── Bolum 28.4: scoring without the embedding service ────────────────

    /**
     * The embedding service is down. Quality drops and the product keeps
     * working, which is the whole point of the fallback.
     */
    @Test
    void theFallbackWeightsIgnoreTheVectorEntirely() {
        var atom = atom(Set.of("go"), Set.of("go"), 0.5);
        var target = new RelevanceScorer.PostingTarget(backendPosting());
        var withVector = new ScorableAtom(atom.atomId(), new float[] {1f, 0f, 0f},
                atom.tags(), atom.skills(), List.of(), atom.importance(), 0.5);

        var scored = RelevanceScorer.score(
                withVector, target, new float[] {1f, 0f, 0f}, ScoringWeights.WITHOUT_EMBEDDING);

        assertThat(scored.components().embedding()).isZero();
        assertThat(scored.score()).isEqualTo(RelevanceScorer.score(
                atom, target, null, ScoringWeights.WITHOUT_EMBEDDING).score());
    }

    @Test
    void bothWeightSetsSumToOne() {
        assertThat(ScoringWeights.DEFAULT.embedding() + ScoringWeights.DEFAULT.tag()
                + ScoringWeights.DEFAULT.skill() + ScoringWeights.DEFAULT.keyword())
                .isCloseTo(1.0, EPSILON);
        assertThat(ScoringWeights.DEFAULT.usesEmbedding()).isTrue();
        assertThat(ScoringWeights.WITHOUT_EMBEDDING.usesEmbedding()).isFalse();
    }

    @Test
    void aweightSetThatDoesNotSumToOneIsRefused() {
        assertThatThrownBy(() -> new ScoringWeights(0.5, 0.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 1.0");
    }

    // ── Absolute rule 7 ──────────────────────────────────────────────────

    /**
     * A Turkish default locale turns "SQL" into "sqı" and no atom would ever
     * match it — the score would drop for every user on a Turkish server and
     * nothing would look broken.
     */
    @Test
    void skillMatchingSurvivesATurkishDefaultLocale() {
        var posting = postingWith(List.of("SQL", "GO"), List.of());
        var atom = atom(Set.of("sql"), Set.of("sql", "go"), 0.5);
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var target = new RelevanceScorer.PostingTarget(posting);

            assertThat(RelevanceScorer.skillOverlap(atom.skills(), target))
                    .isCloseTo(1.0, EPSILON);
        } finally {
            Locale.setDefault(previous);
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private static ScorableAtom atom(Set<String> tags, Set<String> skills, double importance) {
        return new ScorableAtom(
                UUID.randomUUID(), null, tags, skills, List.of(), importance, 0.5);
    }

    private static JobAnalysis backendPosting() {
        return postingWith(List.of("go", "postgres"), List.of("terraform"));
    }

    private static JobAnalysis postingWith(List<String> required, List<String> preferred) {
        return new JobAnalysis(
                new JobAnalysis.Role("Senior Backend Engineer", JobAnalysis.Seniority.SENIOR,
                        "fintech", JobAnalysis.EmploymentType.FULL_TIME,
                        JobAnalysis.WorkMode.REMOTE),
                new JobAnalysis.Company("Acme", JobAnalysis.SizeHint.SCALEUP),
                required.stream().map(name -> new JobAnalysis.Skill(
                        name, name, JobAnalysis.Importance.CRITICAL)).toList(),
                preferred.stream().map(name -> new JobAnalysis.Skill(
                        name, name, null)).toList(),
                List.of("design and scale payment systems"),
                List.of("distributed systems", "high availability"),
                new JobAnalysis.ExperienceYears(5, null),
                List.of("en"), "technical", "en", 0.94, List.of());
    }
}
