package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.compilation.CompilationProperties;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.seed.GoldenProfile;
import com.mustafatetik.atomcv.profile.seed.GoldenProfileReader;
import com.mustafatetik.atomcv.rendering.model.ProfileHeaders;
import com.mustafatetik.atomcv.rendering.latex.LatexDocumentRenderer;
import com.mustafatetik.atomcv.rendering.measurement.MeasurementService;
import com.mustafatetik.atomcv.rendering.measurement.RenderCost;
import com.mustafatetik.atomcv.rendering.measurement.RenderCostService;
import com.mustafatetik.atomcv.rendering.template.CapacityModel.RowShape;
import com.mustafatetik.atomcv.rendering.model.MeasurementRequest;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * about what TeX does, and a claim nobody re-checks decays: this measures every
 * profile in every shipped template against the real compiler and fails when a
 * stored cost has drifted.
 *
 * <p>Run with {@code -Dgolden.record=true} to write the files instead of
 * checking them — after changing a fixture's text, or after the template's
 * geometry moves (EK D.8.9).
 */
@Tag("latex")
@Testcontainers
class GoldenCostsIT {

    /**
     * All three shipped templates, not just the default one.
     *
     * <p>The page guarantee is a claim about a rendered page, and compact and
     * modern render different pages: a 10pt body over a 0.4in margin is not
     * classic with a smaller font, it is a different number of points per row.
     * A golden set that only ever measured classic proved the guarantee for the
     * template nobody had to choose.
     */
    private static final List<TemplateCustomization> TEMPLATES = List.of(
            TemplateCustomization.CLASSIC,
            TemplateCustomization.COMPACT,
            TemplateCustomization.MODERN);

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

        var measuredByTemplate = new LinkedHashMap<String, Map<String, Double>>();
        for (TemplateCustomization template : TEMPLATES) {
            Map<String, Double> measured = measure(golden, template);
                assertThat(measured)
                    .as("every wording and the header came back from one %s compilation",
                            template.costKey())
                    .hasSize(golden.variants().size() + 1);
            measuredByTemplate.put(template.costKey(), measured);
        }

        if (RECORDING) {
            record(name, measuredByTemplate);
            return;
        }

        var byTemplate = GoldenProfileReader.costsOf(name);
        assertThat(byTemplate)
                .as("no costs recorded for %s — run gradlew latexTest -Dgolden.record=true", name)
                .isNotEmpty();
        // The file says which templates it was measured against, and this is
        // where a stale one is caught: a recording made before a geometry
        // change keys its costs under the old version, every lookup misses, and
        // selection quietly falls back to the estimate. A file that names two
        // of the three is the same failure for the template it leaves out.
        assertThat(byTemplate)
                .as("%s was measured against another set of templates — re-record it", name)
                .containsOnlyKeys(measuredByTemplate.keySet().toArray(String[]::new));

        measuredByTemplate.forEach((costKey, measured) -> {
            Map<String, Double> stored = byTemplate.get(costKey);
            measured.forEach((hash, cost) -> assertThat(stored.get(hash))
                    .as("%s under %s has drifted or is missing", shortly(hash), costKey)
                    .isNotNull()
                    .isCloseTo(cost, org.assertj.core.data.Offset.offset(TOLERANCE_PT)));
            assertThat(stored.keySet())
                    .as("a stored %s cost for a wording that no longer exists", costKey)
                    .containsExactlyInAnyOrderElementsOf(measured.keySet());
        });
    }

    /** One compilation for the whole profile, keyed by content hash. */
    private Map<String, Double> measure(GoldenProfile golden, TemplateCustomization template) {
        CapacityModel capacity = TemplateRegistry.capacityOf(template).orElseThrow();
        var client = new LatexCompilerClient(new CompilationProperties(
                "http://" + LATEX.getHost() + ":" + LATEX.getMappedPort(8090),
                Duration.ofSeconds(120)));
        var measurements = new MeasurementService(new LatexDocumentRenderer(), client);

        // The layout each wording is printed in, for the same reason
        // RenderCostService carries it: an INLINE_LIST row reaches the page
        // with its label in bold, and a measurement taken on the unbolded text
        // reports a row narrower than the one that is printed (Bolum 22.4).
        Map<UUID, RowShape> shapeOfAtom = shapes(golden);
        var items = golden.variants().stream()
                .map(variant -> new MeasurementRequest.MeasurableItem(
                        variant.getContentHash(), variant.getContent(),
                        shapeOfAtom.getOrDefault(variant.getAtomId(), RowShape.ENTRY_BULLET)))
                .toList();

        Map<String, RenderCost> costs = measurements.measure(new MeasurementRequest(items,
                template, ProfileHeaders.of(golden.profile(), Locale.ENGLISH)));

        var byHash = new LinkedHashMap<String, Double>();
        // The header block, measured like everything else rather than taken
        // from the template's own constant. It is text: it wraps, and the name
        // is set at a size where a taller glyph is a taller line.
        RenderCost header = costs.get(MeasurementRequest.HEADER_KEY);
        if (header != null) {
            byHash.put(GoldenProfileReader.HEADER_COST, header.heightPt());
        }
        for (AtomVariant variant : golden.variants()) {
            RenderCost cost = costs.get(variant.getContentHash());
            if (cost != null) {
                RowShape shape = shapeOfAtom.getOrDefault(
                        variant.getAtomId(), RowShape.ENTRY_BULLET);
                byHash.put(variant.getContentHash(),
                        cost.totalPt(capacity.itemBaselineSkipPt(),
                                capacity.rowSpacingPt(shape)));
            }
        }
        return byHash;
    }

    /** Which of the three shapes the page sets each atom in. */
    private static Map<UUID, RowShape> shapes(GoldenProfile golden) {
        Map<UUID, SectionLayout> bySection = new LinkedHashMap<>();
        golden.sections().forEach(section ->
                bySection.put(section.getId(), section.getLayout()));
        Map<UUID, RowShape> byAtom = new LinkedHashMap<>();
        golden.atoms().forEach(atom -> {
            SectionLayout layout = bySection.get(atom.getSectionId());
            if (layout != null) {
                byAtom.put(atom.getId(),
                        RenderCostService.shapeOf(layout, atom.getEntryId() != null));
            }
        });
        return byAtom;
    }

    /** Enough of a content hash to find it, and the header block by its name. */
    private static String shortly(String key) {
        return key.length() > 8 ? key.substring(0, 8) : key;
    }

    /** Sorted, so a re-recording produces a diff a person can read. */
    private static void record(String name, Map<String, Map<String, Double>> measured)
            throws Exception {
        Path file = FIXTURES.resolve(name + ".costs.json");
        var sorted = new TreeMap<String, Map<String, Double>>();
        measured.forEach((costKey, costs) -> sorted.put(costKey, new TreeMap<>(costs)));
        JSON.writerWithDefaultPrettyPrinter().writeValue(Files.newBufferedWriter(file), sorted);
        System.out.println("[golden] recorded " + sorted.size() + " templates into " + file);
    }
}
