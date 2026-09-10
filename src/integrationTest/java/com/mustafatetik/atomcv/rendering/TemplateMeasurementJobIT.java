package com.mustafatetik.atomcv.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.rendering.measurement.TemplateMeasurementPayload;
import com.mustafatetik.atomcv.rendering.measurement.TemplateMeasurements;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.HexColor;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What gets asked for, and what the payload survives (Bolum 33.3).
 *
 * <p>The compilation itself is {@code TemplateMeasurementJobIT}'s business in
 * the latex lane; this is about the two things that decide whether it is ever
 * asked for at all — the check that skips a geometry already known, and the
 * payload that has to arrive at a worker describing the same page it left
 * describing.
 */
class TemplateMeasurementJobIT extends AbstractIntegrationTest {

    private static final TemplateCustomization MOVED = new TemplateCustomization(
            "classic", FontFamily.SANS, 9.5, 0.65, 1.25, HexColor.of("1D4ED8"));

    @Autowired
    private TemplateMeasurements measurements;

    @Autowired
    private JobQueue queue;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void startFromNothingQueuedAndNothingMeasured() {
        jdbc.update("DELETE FROM jobs");
        jdbc.update("DELETE FROM template_capacities");
    }

    @Test
    void ageometryNobodyHasMeasuredIsQueued() {
        assertThat(measurements.request(MOVED)).isTrue();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM jobs WHERE type = 'measurement'", Integer.class))
                .isEqualTo(1);
    }

    /**
     * The ordinary case — somebody who never touched a slider — buys no
     * compilation at all.
     */
    @Test
    void abuiltInTemplateIsNeverQueued() {
        assertThat(measurements.request(TemplateCustomization.CLASSIC)).isFalse();
        assertThat(measurements.request(TemplateCustomization.COMPACT)).isFalse();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM jobs", Integer.class)).isZero();
    }

    /**
     * A person dragging a slider back and forth across one setting queues one
     * job, because the key is the geometry rather than the moment.
     */
    @Test
    void askingTwiceForOneGeometryQueuesOneJob() {
        measurements.request(MOVED);
        measurements.request(MOVED);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM jobs WHERE type = 'measurement'", Integer.class))
                .isEqualTo(1);
    }

    /**
     * <strong>The payload has to describe the same page at the other end.</strong>
     * A worker that read back a different font size would measure a document
     * nobody asked for and file it under a key nobody would look up — and
     * every CV at those settings would go on using the estimate forever with
     * nothing saying why.
     */
    @Test
    void thepayloadArrivesDescribingTheSamePage() {
        measurements.request(MOVED);
        Job job = queue.claim("test-reader").flatMap(queue::find).orElseThrow();

        var readBack = TemplateMeasurementPayload.from(job.getPayload()).customization();

        assertThat(readBack.costKey()).isEqualTo(MOVED.costKey());
        assertThat(readBack.fontSizePt()).isEqualTo(9.5);
        assertThat(readBack.marginInches()).isEqualTo(0.65);
        assertThat(readBack.lineSpacing()).isEqualTo(1.25);
        assertThat(readBack.fontFamily()).isEqualTo(FontFamily.SANS);
    }

    /**
     * And the colour does not travel, because it is not what is being
     * measured: Bolum 33.1 puts it in layer A, and the key the answer is filed
     * under does not carry it either.
     */
    @Test
    void thecolourIsNotPartOfWhatIsMeasured() {
        measurements.request(MOVED);
        Job job = queue.claim("test-reader").flatMap(queue::find).orElseThrow();

        assertThat(job.getPayload()).doesNotContainKey("accentColor");
        assertThat(TemplateMeasurementPayload.from(job.getPayload()).customization().costKey())
                .isEqualTo(MOVED.costKey());
    }

    /** Nobody owns it: the answer is as useful to whoever picks it next. */
    @Test
    void themeasurementBelongsToNobody() {
        measurements.request(MOVED);

        assertThat(jdbc.queryForObject(
                "SELECT user_id FROM jobs WHERE type = 'measurement'", String.class)).isNull();
    }
}
