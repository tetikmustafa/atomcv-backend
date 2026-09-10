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
 * Faz C against Bolum 52.6's budget, and against itself (Bolum 52.1).
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
 * <p>Faz C is a pure function (Bolum 17.2), which is what makes this
 * measurable at all: no database, no compiler, no clock inside it. The
 * determinism test rests on the same property.
 */
class SelectionScalingTest {

    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

    /** Enough atoms that the work is measurable; a real profile is 200-ish. */
    private static final int ATOMS = 400;

    /** Warm-up runs, so what is timed is the compiled code and not the interpreter. */
    private static final int WARMUP = 20;

    /** Timed runs. The median of these, because one of them will be a garbage collection. */
    private static final int SAMPLES = 15;

    @Test
    void doublingTheProfileDoesNotQuadrupleTheWork() {
        Duration small = medianOf(profileOf(ATOMS));
        Duration large = medianOf(profileOf(ATOMS * 2));

        double growth = (double) large.toNanos() / Math.max(1, small.toNanos());

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
        Duration median = medianOf(profileOf(ATOMS));

        assertThat(median.toMillis())
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

    private static Duration medianOf(SelectionRequest request) {
        for (int i = 0; i < WARMUP; i++) {
            SelectionPhase.select(request);
        }
        var samples = new ArrayList<Long>(SAMPLES);
        for (int i = 0; i < SAMPLES; i++) {
            long started = System.nanoTime();
            SelectionPhase.select(request);
            samples.add(System.nanoTime() - started);
        }
        samples.sort(Long::compare);
        return Duration.ofNanos(samples.get(samples.size() / 2));
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
