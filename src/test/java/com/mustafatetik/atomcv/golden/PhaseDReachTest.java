package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.rewrite.RewritePlan;
import com.mustafatetik.atomcv.generation.rewrite.RewritePlanner;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScorer;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScores;
import com.mustafatetik.atomcv.generation.scoring.ScorableAtomFactory;
import com.mustafatetik.atomcv.generation.scoring.ScoredAtom;
import com.mustafatetik.atomcv.generation.scoring.ScoringWeights;
import com.mustafatetik.atomcv.generation.selection.SelectionPhase;
import com.mustafatetik.atomcv.generation.selection.SelectionRequestBuilder;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.seed.GoldenProfile;
import com.mustafatetik.atomcv.profile.seed.GoldenProfileReader;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Whether Bolum 21.2's thresholds can be reached at all, measured rather than
 * assumed.
 *
 * <p><strong>They cannot, and this pins the numbers that say so.</strong>
 * {@code RewritePlanner} compares a per-atom relevance score against 0.65 and
 * 0.40, and every test that exercises those tiers hands it a score written by
 * hand — {@code 0.80}, {@code 0.50}, {@code 0.39}. Bolum 51.7's rule is that a
 * component the suite switches off has unverified wiring, and this is the
 * measurement that switches it back on: real profile, real posting, real
 * scorer, and the answer is that Faz D plans nothing.
 *
 * <p><strong>Why, in arithmetic.</strong> The pair below is the best-matched
 * one in the golden set — the master CV against the Java/Spring posting it was
 * really tailored for. Its strongest bullet names three of the posting's
 * <em>seventeen</em> required skills, so Bolum 19.2's skill term reads 0.176:
 * the denominator is everything the posting asked for, and no single sentence
 * covers seventeen requirements. Keyword coverage is 3 of 22. Under
 * {@link ScoringWeights#WITHOUT_EMBEDDING} the best atom reaches
 * <strong>0.126</strong>, against a floor of 0.40.
 *
 * <p><strong>The tag term is measured now, and it is small for a structural
 * reason.</strong> It was 0.0 until the golden profiles carried tags at all,
 * which made a quarter of Bolum 19.1's raw score unreachable in every
 * measurement taken from this fixture set. With tags it is a Jaccard against
 * the posting's own vocabulary — the domain phrase, the keywords and the
 * title's words, twenty-six strings here — so an atom tagged
 * {@code [backend, java, microservices, spring boot]} that hits two of them
 * scores 2/28 = <strong>0.071</strong>. <em>Jaccard divides by the union</em>,
 * and the union is dominated by the posting: a perfectly on-topic atom cannot
 * reach 0.2 on this term unless it carries most of the posting's vocabulary as
 * tags, which no honest tag list does. The term moved the best score from
 * 0.096 to 0.126 and changed no conclusion.
 *
 * <p>With {@link ScoringWeights#DEFAULT} and no vectors it totals 0.276 — and
 * 0.200 of that is the same constant every atom gets, because a missing vector
 * scores the neutral 0.5 (Bolum 28.2) and 0.40 x 0.5 is a pedestal, not a
 * signal. The whole distribution sits between 0.20 and 0.28.
 *
 * <p><strong>What this test is for.</strong> Not to bless the behaviour: to
 * make the next change to either half visible. Move a threshold, change
 * Bolum 19.2's normalisation, or start sending real vectors, and this fails
 * with the new numbers in the message — which is the point at which somebody
 * has to look at whether Faz D now fires on the right sentences.
 */
class PhaseDReachTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

    /** {@code RewritePlanner.FLOOR_SCORE}, which is package-private there. */
    private static final double FLOOR_SCORE = 0.40;

    private static final GoldenProfile PROFILE = GoldenProfileReader.read("master_cv_en", OWNER);
    private static final JobAnalysis POSTING = analysis();

    /**
     * The degraded mode of Bolum 28.4, which is the mode every test lane and
     * every un-embedded atom runs in. Bolum 28.4 calls it a drop in quality;
     * for Faz D it is not a drop, it is off.
     */
    @Test
    void withoutEmbeddingsNoAtomInTheBestMatchedPairReachesTheFloor() {
        SelectionState selection = select(ScoringWeights.WITHOUT_EMBEDDING);

        double best = bestScore(selection);
        assertThat(best)
                .as("the strongest selected atom, master CV against the posting it was "
                        + "written for, with no vectors")
                .isLessThan(FLOOR_SCORE)
                .isCloseTo(0.1259, org.assertj.core.data.Offset.offset(0.0005));

        RewritePlan plan = RewritePlanner.plan(PROFILE.tree(), selection);
        assertThat(plan.candidates())
                .as("Faz D plans nothing at all for the pair it should have the most to say "
                        + "about")
                .isEmpty();
    }

    /**
     * And with the embedding weight restored but no vector to spend it on, the
     * neutral value becomes a constant every atom carries — so the scores move
     * up together and the distance to the floor stays.
     */
    @Test
    void withTheDefaultWeightsAndNoVectorsEveryScoreSitsOnTheNeutralPedestal() {
        List<Double> scores = RelevanceScorer.rank(
                        ScorableAtomFactory.from(PROFILE.tree(), PROFILE.tagsByAtom(), TODAY),
                        POSTING, ScoringWeights.DEFAULT).stream()
                .map(atom -> atom.score())
                .toList();

        double pedestal = ScoringWeights.DEFAULT.embedding() * 0.5;
        assertThat(pedestal).isEqualTo(0.20);
        assertThat(scores).isNotEmpty().allSatisfy(score ->
                assertThat(score).isGreaterThanOrEqualTo(pedestal).isLessThan(FLOOR_SCORE));

        assertThat(scores.stream().mapToDouble(Double::doubleValue).max().orElseThrow())
                .isCloseTo(0.2756, org.assertj.core.data.Offset.offset(0.0005));
    }

    /**
     * <strong>The tag term is switched on, and this is what switches it
     * on.</strong> Bolum 51.7's rule again: a component the whole suite
     * disables has unverified wiring, and a quarter of Bolum 19.1's raw score
     * was exactly that until the golden profiles carried tags — every number
     * this class pinned was taken against a term that could only be zero.
     *
     * <p>So the assertion is not about a value. It is that <em>some</em> atom
     * in the best-matched pair scores above zero on the term: a fixture set
     * tagged only with themes nobody's posting spells would pass every other
     * test here and leave the component as dead as it was.
     */
    @Test
    void thetagTermIsReachedByTheFixtureSet() {
        List<ScoredAtom> ranked = RelevanceScorer.rank(
                ScorableAtomFactory.from(PROFILE.tree(), PROFILE.tagsByAtom(), TODAY),
                POSTING, ScoringWeights.WITHOUT_EMBEDDING);

        assertThat(PROFILE.tagsByAtom())
                .as("the fixture carries tags at all")
                .isNotEmpty();
        assertThat(ranked)
                .as("at least one atom's tags touch the posting's vocabulary")
                .anySatisfy(atom -> assertThat(atom.components().tag()).isGreaterThan(0.0));
    }

    /**
     * And the ceiling on that term, which is the reason it moved the score by
     * three hundredths rather than by a quarter.
     *
     * <p>Jaccard divides by the <em>union</em>, and the union is the posting's
     * whole vocabulary: twenty-six strings for this one. An atom carrying four
     * tags, every one of them a hit, would score 4/26. Nothing a person would
     * actually write reaches that — which is a property of Bolum 19.1's choice
     * of measure, not of this fixture, and it is worth having written down
     * beside the numbers it explains.
     */
    @Test
    void thetagTermIsBoundedByThePostingsOwnVocabulary() {
        double best = RelevanceScorer.rank(
                        ScorableAtomFactory.from(PROFILE.tree(), PROFILE.tagsByAtom(), TODAY),
                        POSTING, ScoringWeights.WITHOUT_EMBEDDING).stream()
                .mapToDouble(atom -> atom.components().tag())
                .max().orElseThrow();

        assertThat(best)
                .as("the best tag overlap in the best-matched pair")
                .isCloseTo(0.0714, org.assertj.core.data.Offset.offset(0.0005));
    }

    /**
     * The cause, stated as a number rather than as prose: the denominator of
     * Bolum 19.2's skill term is the posting's requirement list, and this
     * posting has seventeen of them.
     */
    @Test
    void thepostingAsksForMoreThanOneSentenceCanCarry() {
        assertThat(POSTING.requiredSkills()).hasSizeGreaterThan(10);

        // The most on-posting bullet in the profile. Three of seventeen is the
        // best any single sentence does, and 3/17 is 0.176 of the skill term.
        assertThat(3.0 / POSTING.requiredSkills().size()).isLessThan(0.20);
    }

    private static double bestScore(SelectionState selection) {
        return selection.selected().stream()
                .mapToDouble(SelectionState.SelectedAtom::score)
                .max().orElseThrow();
    }

    private static SelectionState select(ScoringWeights weights) {
        ProfileTree tree = PROFILE.tree();
        var scores = new RelevanceScores(
                RelevanceScorer.rank(
                        ScorableAtomFactory.from(tree, PROFILE.tagsByAtom(), TODAY),
                        POSTING, weights),
                weights);
        var built = SelectionRequestBuilder.build(
                tree, TemplateCustomization.CLASSIC, CAPACITY, 1, "en", Tone.FORMAL, scores);
        return SelectionPhase.select(built.request()).orElseThrow();
    }

    private static JobAnalysis analysis() {
        String path = "golden/analyses/senior_java_spring_en.json";
        try (InputStream in = PhaseDReachTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("No such golden analysis: " + path);
            }
            return new ObjectMapper().readValue(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8), JobAnalysis.class);
        } catch (java.io.IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
    }
}
