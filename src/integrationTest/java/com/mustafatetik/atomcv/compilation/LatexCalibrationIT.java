package com.mustafatetik.atomcv.compilation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.rendering.latex.LatexDocumentRenderer;
import com.mustafatetik.atomcv.rendering.measurement.TexLogParser;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The numbers the page guarantee is built on, re-derived from the compiler
 * (Bolum 26.4).
 *
 * <p>{@code TemplateRegistry} stores what the classic template's furniture
 * costs. Those were measured once; this measures them again every time it
 * runs. When the preamble changes — a different rule, a different spacing —
 * these fail, and that is the signal to re-measure and raise the template
 * version rather than let stored costs quietly describe a document that no
 * longer exists.
 */
@Tag("latex")
@Testcontainers
class LatexCalibrationIT {

    private static final double TOLERANCE_PT = 0.01;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Container
    static final GenericContainer<?> LATEX = new GenericContainer<>(
            new ImageFromDockerfile("atomcv-latex-test", false)
                    .withFileFromPath(".", Path.of("docker/latex")))
            .withExposedPorts(8090)
            .withStartupTimeout(Duration.ofMinutes(5));

    private static Map<String, Double> probes;

    @BeforeAll
    static void measure() throws Exception {
        var source = new LatexDocumentRenderer()
                .renderCalibration(TemplateCustomization.CLASSIC);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + LATEX.getHost() + ":"
                        + LATEX.getMappedPort(8090) + "/measure"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(source.value(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        probes = TexLogParser.parseCalibration(new String(response.body(), StandardCharsets.UTF_8));
        assertThat(probes).as("the calibration document has to compile").isNotEmpty();
    }

    @Test
    void thePageIsAsTallAsTheStoredCapacitySays() {
        assertThat(probes.get("textheight"))
                .isCloseTo(capacity().pageTextHeightPt(), offset());
    }

    @Test
    void theLineIsAsWideAsTheStoredCapacitySays() {
        // The width an estimate divides by. Wrong here, and every unmeasured
        // atom is charged for the wrong number of lines (EK D.8.7).
        assertThat(probes.get("textwidth"))
                .isCloseTo(capacity().textWidthPt(), offset());
    }

    @Test
    void theBaselineSkipMatches() {
        assertThat(probes.get("baselineskip"))
                .isCloseTo(capacity().baselineSkipPt(), offset());
    }

    @Test
    void theHeaderBlockCostsWhatWasMeasured() {
        assertThat(delta("start", "afterHeaderBlock"))
                .isCloseTo(capacity().fixedCost(CapacityModel.HEADER_BLOCK), offset());
    }

    @Test
    void aSectionHeadingCostsWhatWasMeasured() {
        assertThat(delta("afterHeaderBlock", "afterSection"))
                .isCloseTo(capacity().fixedCost(CapacityModel.SECTION_HEADER), offset());
    }

    @Test
    void anEntryHeadingCostsWhatWasMeasured() {
        assertThat(delta("afterSection", "afterEntry"))
                .isCloseTo(capacity().fixedCost(CapacityModel.ENTRY_HEADER), offset());
    }

    /**
     * A bullet list of one, then of three: the difference gives one bullet,
     * and what is left over is the list's own overhead.
     */
    @Test
    void aBulletListSeparatesIntoOverheadAndLines() {
        double oneItemBlock = delta("afterEntry", "afterOneItem");
        double threeItemBlock = delta("afterOneItem", "afterThreeItems");
        double perItem = (threeItemBlock - oneItemBlock) / 2;

        assertThat(perItem)
                .isCloseTo(capacity().fixedCost(CapacityModel.ITEM_LINE), offset());
        assertThat(oneItemBlock - perItem)
                .isCloseTo(capacity().fixedCost(CapacityModel.ITEMIZE_OVERHEAD), offset());
    }

    /**
     * The furniture has to cost the same the second time (EK D.8.10).
     *
     * <p>The stored constants were measured from one section, one entry and
     * one list. A document has several of each, and a per-repetition
     * difference would show up as drift no single measurement could explain —
     * which is exactly what {@code MeasurementDriftIT} found.
     */
    @Test
    void aSecondSectionEntryAndListCostWhatTheFirstOnesDid() {
        assertThat(delta("afterThreeItems", "afterSecondSection"))
                .as("a section heading further down the page")
                .isCloseTo(capacity().fixedCost(CapacityModel.SECTION_HEADER), offset());
        assertThat(delta("afterSecondSection", "afterSecondEntry"))
                .as("an entry heading further down the page")
                .isCloseTo(capacity().fixedCost(CapacityModel.ENTRY_HEADER), offset());
        assertThat(delta("afterSecondEntry", "afterSecondList"))
                .as("a one-item list further down the page")
                .isCloseTo(capacity().fixedCost(CapacityModel.ITEMIZE_OVERHEAD)
                        + capacity().fixedCost(CapacityModel.ITEM_LINE), offset());
    }

    /**
     * The second job of a career, and every one after it (EK D.8.10).
     *
     * <p>An entry heading is not one number. After a section heading it costs
     * what {@code ENTRY_HEADER} says; after the bullet list of the job above
     * it, the paragraph skip applies and it costs nine points more.
     */
    @Test
    void anEntryFollowingAListCostsMoreThanOneFollowingAHeading() {
        assertThat(delta("afterSecondList", "afterEntryFollowingAList"))
                .isCloseTo(capacity().fixedCost(CapacityModel.ENTRY_HEADER_AFTER_LIST), offset());
        assertThat(capacity().fixedCost(CapacityModel.ENTRY_HEADER_AFTER_LIST))
                .isGreaterThan(capacity().fixedCost(CapacityModel.ENTRY_HEADER));
    }

    @Test
    void anUncalibratedCustomizationHasNoCapacityAtAll() {
        // Bolum 33.1's layer B: font size, family, margin and spacing all move
        // these numbers, and a guessed capacity is how a page guarantee breaks
        // without an error.
        var different = new TemplateCustomization("classic",
                TemplateCustomization.CLASSIC.fontFamily(), 12.0, 0.6, 1.0,
                TemplateCustomization.CLASSIC.accentColor());

        assertThat(new LatexDocumentRenderer().capacity(different)).isEmpty();
    }

    private static CapacityModel capacity() {
        return new LatexDocumentRenderer().capacity(TemplateCustomization.CLASSIC).orElseThrow();
    }

    private static double delta(String from, String to) {
        return probes.get(to) - probes.get(from);
    }

    private static org.assertj.core.data.Offset<Double> offset() {
        return org.assertj.core.data.Offset.offset(TOLERANCE_PT);
    }

}
