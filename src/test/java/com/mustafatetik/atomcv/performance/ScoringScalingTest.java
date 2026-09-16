package com.mustafatetik.atomcv.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScorer;
import com.mustafatetik.atomcv.generation.scoring.ScorableAtom;
import com.mustafatetik.atomcv.generation.scoring.ScoringWeights;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Faz B against the budget, and against itself.
 *
 * <p><strong>The budget file set a figure for this phase and nothing read
 * it</strong> (denetim, beşinci tur). {@code phase_scoring} sat in
 * {@code performance-budgets.yaml} under a header saying "read by tests" with
 * no test anywhere holding it — the sibling {@link SelectionScalingTest} holds
 * Faz C and was taken for coverage of both. A number in a budget file that
 * nothing asserts is not a budget, it is a comment that looks enforceable.
 *
 * <p>The method is that sibling's, and so is the reasoning: the ratio is the
 * real assertion and the milliseconds are the loose one, because a CI
 * machine's speed varies by more than an honest threshold would measure, while
 * comparing one run to another <em>on the same machine</em> catches the loop
 * inside a loop whatever the box is doing.
 *
 * <p>Faz B is measurable for the same reason Faz C is: once the vectors are
 * there it is arithmetic over atoms in memory, with no database, no service
 * and no clock inside it. <strong>The vectors are supplied here</strong> — the
 * embedding call that fetches them is a network round trip and is not part of
 * what this measures.
 *
 * <p><strong>Measured on this machine, 2026-09-16:</strong> 3 ms for four
 * hundred atoms against a budget of 180, and a growth of 1.34. Below two
 * because the smallest doubling is the one where per-call overhead still
 * counts, and the minimum is deliberately the conservative end of the curve.
 *
 * <p><strong>What a planted fault reads, and the part worth knowing:</strong>
 * wrapping the call in a loop that makes the work n²/25 moved the growth from
 * 1.34 to <strong>2.94</strong> — a large, unmistakable signal, and still
 * under the ceiling of 3.0. So this guard catches quadratic work by the size
 * of the jump rather than by the threshold being crossed, and a mildly
 * quadratic change could sit just under it. Raising the ceiling's sensitivity
 * is a budget decision and belongs in a pull request that says so, not in a
 * number quietly edited next to the test it governs; the same is true of the
 * sibling, whose method this is.
 */
class ScoringScalingTest {

    /** A real profile is 200-ish; this is comfortably past it. */
    private static final int ATOMS = 400;

    private static final int WARMUP = 20;

    private static final int SAMPLES = 15;

    /** BGE-M3's width, so the cosine loop runs over what it really runs over. */
    private static final int DIMENSIONS = 1024;

    private static final JobAnalysis POSTING = posting();

    private static final float[] POSTING_VECTOR = vector(7);

    @Test
    void doublingTheProfileDoesNotQuadrupleTheWork() {
        double growth = smallestGrowthAcrossTheCurve();

        assertThat(growth)
                .as("linear is about 2, quadratic is about 4; the budget sits between")
                .isLessThanOrEqualTo(PerformanceBudgets.maxGrowthWhenInputDoubles());
    }

    /**
     * And the absolute figure, held loosely — here because the budget asks for
     * it and a file nothing reads is a file nobody maintains, not because a
     * millisecond count on this machine means anything about production.
     */
    @Test
    void scoringStaysInsideItsBudget() {
        Duration fastest = fastestOf(profileOf(ATOMS));

        assertThat(fastest.toMillis())
                .as("the budget figure, two to three times the measured one")
                .isLessThanOrEqualTo(PerformanceBudgets.backendP95Millis("phase_scoring"));
    }

    /**
     * A run that scored nothing, or scored everything the same, would make both
     * numbers above meaningless — the sort is part of the work being timed.
     */
    @Test
    void theworkActuallyHappens() {
        var ranked = RelevanceScorer.rank(
                profileOf(ATOMS), POSTING, POSTING_VECTOR, ScoringWeights.DEFAULT);

        assertThat(ranked).hasSize(ATOMS);
        assertThat(ranked.get(0).score())
                .as("the best atom outranks the worst, so the ordering really ran")
                .isGreaterThan(ranked.get(ranked.size() - 1).score());
    }

    private static final int[] CURVE = {100, 200, 400, 800, 1600};

    /**
     * The smallest growth across the curve, every size timed in one rotation.
     *
     * <p>Both of the measurement faults the sibling records were faults of the
     * test rather than of the code, and the shape that avoids them is copied
     * whole: interleave the sizes so a collection cannot be attributed to the
     * larger input, take several doublings rather than one, and take the
     * <em>smallest</em> ratio — noise only ever inflates a ratio, so the
     * minimum is the closest estimate of the real growth and quadratic work
     * cannot hide under it.
     */
    private static double smallestGrowthAcrossTheCurve() {
        var profiles = new ArrayList<List<ScorableAtom>>();
        for (int size : CURVE) {
            profiles.add(profileOf(size));
        }
        for (int i = 0; i < WARMUP; i++) {
            profiles.forEach(ScoringScalingTest::rank);
        }

        long[] fastest = new long[CURVE.length];
        java.util.Arrays.fill(fastest, Long.MAX_VALUE);
        for (int sample = 0; sample < SAMPLES; sample++) {
            for (int i = 0; i < CURVE.length; i++) {
                long started = System.nanoTime();
                rank(profiles.get(i));
                fastest[i] = Math.min(fastest[i], System.nanoTime() - started);
            }
        }

        double smallest = Double.MAX_VALUE;
        for (int i = 1; i < CURVE.length; i++) {
            smallest = Math.min(smallest,
                    (double) fastest[i] / Math.max(1, fastest[i - 1]));
        }
        return smallest;
    }

    private static Duration fastestOf(List<ScorableAtom> atoms) {
        for (int i = 0; i < WARMUP; i++) {
            rank(atoms);
        }
        long fastest = Long.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            long started = System.nanoTime();
            rank(atoms);
            fastest = Math.min(fastest, System.nanoTime() - started);
        }
        return Duration.ofNanos(fastest);
    }

    private static List<com.mustafatetik.atomcv.generation.scoring.ScoredAtom> rank(
            List<ScorableAtom> atoms) {
        return RelevanceScorer.rank(atoms, POSTING, POSTING_VECTOR, ScoringWeights.DEFAULT);
    }

    /**
     * Atoms that differ from each other, which matters: identical ones would
     * let the sort finish early and every cache hit, and the timing would be
     * of a profile nobody has.
     */
    private static List<ScorableAtom> profileOf(int atoms) {
        var profile = new ArrayList<ScorableAtom>(atoms);
        for (int i = 0; i < atoms; i++) {
            profile.add(new ScorableAtom(UUID.randomUUID(), vector(i),
                    Set.of("backend", "distributed systems", "tag" + (i % 40)),
                    Set.of("go", "postgres", "skill" + (i % 25)),
                    RelevanceScorer.tokensOf(
                            "Ran distributed systems on call for service number " + i),
                    0.5 + ((i % 5) * 0.1), 0.5));
        }
        return profile;
    }

    /** Deterministic and seeded by position, so a run is repeatable. */
    private static float[] vector(int seed) {
        float[] values = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            values[i] = (float) Math.sin((seed + 1) * 0.001 * (i + 1));
        }
        return values;
    }

    private static JobAnalysis posting() {
        return new JobAnalysis(
                new JobAnalysis.Role("Senior Backend Engineer", JobAnalysis.Seniority.SENIOR,
                        "fintech", JobAnalysis.EmploymentType.FULL_TIME,
                        JobAnalysis.WorkMode.REMOTE),
                new JobAnalysis.Company("Acme", JobAnalysis.SizeHint.SCALEUP),
                List.of(new JobAnalysis.Skill("go", "go", JobAnalysis.Importance.CRITICAL),
                        new JobAnalysis.Skill("postgres", "postgres",
                                JobAnalysis.Importance.CRITICAL)),
                List.of(new JobAnalysis.Skill("terraform", "terraform", null)),
                List.of("design and scale payment systems"),
                List.of("distributed systems", "high availability"),
                new JobAnalysis.ExperienceYears(5, null),
                List.of("en"), "technical", "en", 0.94, List.of());
    }
}
