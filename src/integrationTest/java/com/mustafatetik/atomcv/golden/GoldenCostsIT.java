package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.compilation.CompilationProperties;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.seed.GoldenProfile;
import com.mustafatetik.atomcv.profile.seed.GoldenProfileReader;
import com.mustafatetik.atomcv.rendering.latex.LatexDocumentRenderer;
import com.mustafatetik.atomcv.rendering.measurement.MeasurementService;
import com.mustafatetik.atomcv.rendering.measurement.RenderCost;
import com.mustafatetik.atomcv.rendering.model.MeasurementRequest;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The measured costs of the golden set, kept honest (Bolum 51.3).
 *
 * <p>The golden tests run without Docker, which is only possible because the
 * costs are committed next to the fixtures. A committed number is a claim
 * about what TeX does, and a claim nobody re-checks decays: this measures all
 * five profiles against the real compiler and fails when a stored cost has
 * drifted.
 *
 * <p>Run with {@code -Dgolden.record=true} to write the files instead of
 * checking them — after changing a fixture's text, or after the template's
 * geometry moves (EK D.8.9).
 */
@Tag("latex")
@Testcontainers
class GoldenCostsIT {

    private static final String COST_KEY = TemplateCustomization.CLASSIC.costKey();
    private static final double TOLERANCE_PT = 0.01;
    private static final Path FIXTURES = Path.of("src/main/resources/golden/profiles");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final boolean RECORDING = Boolean.getBoolean("golden.record");

    @Container
    static final GenericContainer<?> LATEX = new GenericContainer<>(
            new ImageFromDockerfile("atomcv-latex-test", false)
                    .withFileFromPath(".", Path.of("docker/latex")))
            .withExposedPorts(8090)
            .withStartupTimeout(Duration.ofMinutes(5));

    static java.util.stream.Stream<String> names() {
        return GoldenProfileReader.NAMES.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void theStoredCostsAreWhatTheCompilerSays(String name) throws Exception {
        GoldenProfile golden = GoldenProfileReader.read(name, UUID.randomUUID());
        Map<String, Double> measured = measure(golden);

        assertThat(measured).as("every wording came back from one compilation")
                .hasSameSizeAs(golden.variants());

        if (RECORDING) {
            record(name, measured);
            return;
        }

        var byTemplate = GoldenProfileReader.costsOf(name);
        assertThat(byTemplate)
                .as("no costs recorded for %s — run gradlew latexTest -Dgolden.record=true", name)
                .isNotEmpty();
        // The file says which template it was measured against, and this is
        // where a stale one is caught: a recording made before a geometry
        // change keys its costs under the old version, every lookup misses, and
        // selection quietly falls back to the estimate.
        assertThat(byTemplate)
                .as("%s was measured against another template version — re-record it", name)
                .containsOnlyKeys(COST_KEY);
        Map<String, Double> stored = byTemplate.get(COST_KEY);

        measured.forEach((hash, cost) -> assertThat(stored.get(hash))
                .as("wording %s has drifted or is missing", hash.substring(0, 8))
                .isNotNull()
                .isCloseTo(cost, org.assertj.core.data.Offset.offset(TOLERANCE_PT)));
        assertThat(stored.keySet())
                .as("a stored cost for a wording that no longer exists")
                .containsExactlyInAnyOrderElementsOf(measured.keySet());
    }

    /** One compilation for the whole profile, keyed by content hash. */
    private Map<String, Double> measure(GoldenProfile golden) {
        CapacityModel capacity =
                TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();
        var client = new LatexCompilerClient(new CompilationProperties(
                "http://" + LATEX.getHost() + ":" + LATEX.getMappedPort(8090),
                Duration.ofSeconds(120)));
        var measurements = new MeasurementService(new LatexDocumentRenderer(), client);

        var items = golden.variants().stream()
                .map(variant -> new MeasurementRequest.MeasurableItem(
                        variant.getContentHash(), variant.getContent()))
                .toList();

        Map<String, RenderCost> costs = measurements.measure(
                new MeasurementRequest(items, TemplateCustomization.CLASSIC));

        var byHash = new LinkedHashMap<String, Double>();
        for (AtomVariant variant : golden.variants()) {
            RenderCost cost = costs.get(variant.getContentHash());
            if (cost != null) {
                byHash.put(variant.getContentHash(),
                        cost.totalPt(capacity.baselineSkipPt(), capacity.itemSpacingPt()));
            }
        }
        return byHash;
    }

    /** Sorted, so a re-recording produces a diff a person can read. */
    private static void record(String name, Map<String, Double> measured) throws Exception {
        Path file = FIXTURES.resolve(name + ".costs.json");
        JSON.writerWithDefaultPrettyPrinter().writeValue(Files.newBufferedWriter(file),
                Map.of(COST_KEY, new TreeMap<>(measured)));
        System.out.println("[golden] recorded " + measured.size() + " costs into " + file);
    }
}
