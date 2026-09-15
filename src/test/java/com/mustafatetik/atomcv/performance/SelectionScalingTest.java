package com.mustafatetik.atomcv.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.selection.SelectionPhase;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.AtomCandidate;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.EntryPlan;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.SectionPlan;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Faz C against the budget, and against itself.
 *
 * <p><strong>The ratio is the real assertion and the milliseconds are the
 * loose one.</strong> A CI machine's speed varies by more than any honest
 * budget would measure, so a tight threshold fails for the weather; Bolum 52.6
 * says as much and asks for a figure two or three times the real one. What
 * catches the thing worth catching — somebody adding a loop inside a loop — is
 * comparing one run to another <em>on the same machine</em>: linear work
 * doubles when the input doubles and quadratic work quadruples, so a ceiling
 * between the two says which happened however slow the box is.
 *
 * <p>Faz C is a pure function, which is what makes this measurable at all: no
 * database, no compiler, no clock inside it. The determinism test rests on the
 * same property.
 */
class SelectionScalingTest {

    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

    /** Enough atoms that the work is measurable; a real profile is 200-ish. */
    private static final int ATOMS = 400;

    /** Warm-up runs, so what is timed is the compiled code and not the interpreter. */
    private static final int WARMUP = 20;

    /** Timed runs. The fastest of these is the one nothing interrupted. */
    private static final int SAMPLES = 15;

    @Test
    void doublingTheProfileDoesNotQuadrupleTheWork() {
        double growth = smallestGrowthAcrossTheCurve();

        assertThat(growth)
                .as("linear is about 2, quadratic is about 4; the budget sits between")
                .isLessThanOrEqualTo(PerformanceBudgets.maxGrowthWhenInputDoubles());
    }

    /**
     * And the absolute figure, held loosely. It is here because Bolum 52.6
     * asks for a budget file and a file nothing reads is a file nobody
     * maintains — not because a millisecond count on this machine means
     * anything about production.
     */
    @Test
    void selectionStaysInsideItsBudget() {
        Duration fastest = fastestOf(profileOf(ATOMS));

        assertThat(fastest.toMillis())
                .as("Bolum 52.6's figure, two to three times Bolum 52.1's")
                .isLessThanOrEqualTo(PerformanceBudgets.backendP95Millis("phase_selection"));
    }

    /** A run that selected nothing would make both numbers above meaningless. */
    @Test
    void theworkActuallyHappens() {
        var state = SelectionPhase.select(profileOf(ATOMS)).orElseThrow();

        assertThat(state.selected()).isNotEmpty();
        assertThat(state.rejected())
                .as("four hundred atoms do not fit one page, so it really packed")
                .isNotEmpty();
    }

    /** Four doublings rather than one, all timed in the same rotation. */
    private static final int[] CURVE = {100, 200, 400, 800, 1600};

    /**
     * The smallest growth across the whole curve, every size timed in one
     * rotation.
     *
     * <p><strong>Two measurement faults were found here and both were the
     * test.</strong> The first: timing one size to completion and then the
     * other attributes everything that changed about the JVM in between to the
     * input size. Run alone this passed every time; run after seventeen hundred
     * others it read 3.63 against a ceiling of 3.0. Interleaving the pair
     * brought it to 3.22 — better and still wrong.
     *
     * <p>The second: one doubling is one sample. Measured across five sizes in
     * one rotation the curve is <strong>1.99, 2.03, 2.03, 2.09</strong> — the
     * algorithm is linear and always was, and the pair that failed was the pair
     * that happened to catch a collection.
     *
     * <p><strong>The smallest ratio, because noise only ever inflates.</strong>
     * A pause makes the larger side look slower and the ratio bigger; nothing
     * makes it smaller. So the minimum across four doublings is the closest
     * estimate of the real growth, and a genuinely quadratic implementation
     * reads about four on <em>every</em> doubling — there is no way for it to
     * hide under a minimum.
     *
     * <p><strong>Widening the ceiling was the other option and it is still the
     * wrong one</strong> — the ceiling is what separates linear work from
     * quadratic, and there is no room to give away between 2 and 4.
     */
    private static double smallestGrowthAcrossTheCurve() {
        var requests = new ArrayList<SelectionRequest>();
        for (int size : CURVE) {
            requests.add(profileOf(size));
        }
        for (int i = 0; i < WARMUP; i++) {
            requests.forEach(SelectionPhase::select);
        }

        long[] fastest = new long[CURVE.length];
        java.util.Arrays.fill(fastest, Long.MAX_VALUE);
        for (int sample = 0; sample < SAMPLES; sample++) {
            for (int i = 0; i < CURVE.length; i++) {
                long started = System.nanoTime();
                SelectionPhase.select(requests.get(i));
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

    /** The one-size figure the budget is about. */
    private static Duration fastestOf(SelectionRequest request) {
        for (int i = 0; i < WARMUP; i++) {
            SelectionPhase.select(request);
        }
        long fastest = Long.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            long started = System.nanoTime();
            SelectionPhase.select(request);
            fastest = Math.min(fastest, System.nanoTime() - started);
        }
        return Duration.ofNanos(fastest);
    }

    /** Entries of five bullets each, which is the shape a real profile has. */
    private static SelectionRequest profileOf(int atoms) {
        var entries = new ArrayList<EntryPlan>();
        for (int entry = 0; entry < atoms / 5; entry++) {
            UUID entryId = UUID.randomUUID();
            var candidates = new ArrayList<AtomCandidate>();
            for (int bullet = 0; bullet < 5; bullet++) {
                candidates.add(new AtomCandidate(UUID.randomUUID(), UUID.randomUUID(), entryId,
                        0.5 + (bullet * 0.01), 25.0, false, true));
            }
            entries.add(new EntryPlan(entryId, (short) 2, candidates));
        }
        return new SelectionRequest(
                List.of(new SectionPlan(UUID.randomUUID(), false, entries, List.of())),
                1, CAPACITY);
    }
}
