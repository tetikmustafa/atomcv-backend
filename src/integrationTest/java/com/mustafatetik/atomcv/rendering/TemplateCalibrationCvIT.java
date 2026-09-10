package com.mustafatetik.atomcv.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractLatexTest;
import com.mustafatetik.atomcv.jobs.queue.JobEvents;
import com.mustafatetik.atomcv.jobs.queue.JobHandler;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.workers.JobWorker;
import com.mustafatetik.atomcv.jobs.workers.JobWorkerProperties;
import com.mustafatetik.atomcv.rendering.measurement.Capacities;
import com.mustafatetik.atomcv.rendering.measurement.CalibrationService;
import com.mustafatetik.atomcv.rendering.measurement.TemplateMeasurements;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.HexColor;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Layer B's loop, closed (Bolum 33.3).
 *
 * <p>A geometry nobody has compiled is estimated, a measurement is asked for,
 * a worker runs it against a real compiler, and the same geometry is exact
 * afterwards. Every step of that exists in its own test; this is the only
 * thing that says they are the same three steps.
 */
@Tag("latex")
@ActiveProfiles({"local", "local-fake"})
class TemplateCalibrationCvIT extends AbstractLatexTest {

    private static final TemplateCustomization MOVED = new TemplateCustomization(
            "classic", FontFamily.SANS, 9.5, 0.65, 1.25, HexColor.of("000000"));

    @Autowired
    private TemplateMeasurements measurements;

    @Autowired
    private Capacities capacities;

    @Autowired
    private CalibrationService calibration;

    @Autowired
    private JobQueue queue;

    @Autowired
    private List<JobHandler> handlers;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void startFromNothingMeasured() {
        jdbc.update("DELETE FROM jobs");
        jdbc.update("DELETE FROM template_capacities");
    }

    @Test
    void ageometryIsEstimatedThenMeasuredThenExact() {
        assertThat(capacities.resolve(MOVED).orElseThrow().estimated())
                .as("nobody has compiled this yet")
                .isTrue();

        assertThat(measurements.request(MOVED)).isTrue();
        assertThat(worker().runOne()).as("the queued calibration was taken").isTrue();

        var resolved = capacities.resolve(MOVED).orElseThrow();
        assertThat(resolved.estimated()).as("and now it is measured").isFalse();
        assertThat(resolved.budgetFactor())
                .as("so the whole page is spent rather than 92% of it")
                .isEqualTo(1.0);
    }

    /**
     * And what landed is what the compiler said, not what the estimate
     * guessed. Without this the loop could close on the estimate itself and
     * every assertion above would still pass.
     */
    @Test
    void whatLandsIsTheMeasurementAndNotTheEstimate() {
        CapacityModel truth = calibration.measure(MOVED).orElseThrow();
        jdbc.update("DELETE FROM template_capacities");

        measurements.request(MOVED);
        worker().runOne();

        CapacityModel stored = capacities.find(MOVED).orElseThrow();
        assertThat(stored.pageTextHeightPt())
                .isCloseTo(truth.pageTextHeightPt(), Offset.offset(0.01));
        assertThat(stored.itemBaselineSkipPt())
                .isCloseTo(truth.itemBaselineSkipPt(), Offset.offset(0.01));
        assertThat(stored.fixedCost(CapacityModel.SECTION_HEADER))
                .isCloseTo(truth.fixedCost(CapacityModel.SECTION_HEADER), Offset.offset(0.01));
    }

    /** Asked for twice, compiled once: the second run has nothing to do. */
    @Test
    void asecondRequestForOneGeometryIsNotAsecondCompilation() {
        measurements.request(MOVED);
        worker().runOne();

        assertThat(measurements.request(MOVED))
                .as("it is measured now, so nothing is queued")
                .isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM jobs WHERE type = 'measurement'", Integer.class))
                .isEqualTo(1);
    }

    private JobWorker worker() {
        return new JobWorker(queue, JobEvents.NONE, handlers,
                new JobWorkerProperties(true, 1, null, null, null, Duration.ofSeconds(5)),
                clock);
    }
}
