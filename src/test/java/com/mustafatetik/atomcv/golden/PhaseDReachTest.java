package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.rewrite.RewritePlan;
import com.mustafatetik.atomcv.generation.rewrite.RewritePlanner;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScorer;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScores;
import com.mustafatetik.atomcv.generation.scoring.ScorableAtomFactory;
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
import java.util.Map;
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
 * covers seventeen requirements. The tag term is 0.0 because the profile
 * carries no tags, which is true of any profile whose owner has not tagged it.
 * Keyword coverage is 3 of 22. Under {@link ScoringWeights#WITHOUT_EMBEDDING}
 * that totals <strong>0.064</strong>, against a floor of 0.40.
 *
 * <p>With {@link ScoringWeights#DEFAULT} and no vectors it totals 0.258 — and
 * 0.200 of that is the same constant every atom gets, because a missing vector
 * scores the neutral 0.5 (Bolum 28.2) and 0.40 x 0.5 is a pedestal, not a
 * signal. The whole distribution sits between 0.20 and 0.26.
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
                .isCloseTo(0.0959, org.assertj.core.data.Offset.offset(0.0005));

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
                        ScorableAtomFactory.from(PROFILE.tree(), Map.of(), TODAY),
                        POSTING, ScoringWeights.DEFAULT).stream()
                .map(atom -> atom.score())
                .toList();

        double pedestal = ScoringWeights.DEFAULT.embedding() * 0.5;
        assertThat(pedestal).isEqualTo(0.20);
        assertThat(scores).isNotEmpty().allSatisfy(score ->
                assertThat(score).isGreaterThanOrEqualTo(pedestal).isLessThan(FLOOR_SCORE));

        assertThat(scores.stream().mapToDouble(Double::doubleValue).max().orElseThrow())
                .isCloseTo(0.2578, org.assertj.core.data.Offset.offset(0.0005));
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
                        ScorableAtomFactory.from(tree, Map.of(), TODAY), POSTING, weights),
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
