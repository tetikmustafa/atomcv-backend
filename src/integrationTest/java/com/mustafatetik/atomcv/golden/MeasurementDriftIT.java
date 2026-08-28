package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.compilation.CompilationProperties;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.generation.render.RenderPhase;
import com.mustafatetik.atomcv.generation.selection.SelectionPhase;
import com.mustafatetik.atomcv.generation.selection.SelectionRequestBuilder;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.seed.GoldenProfile;
import com.mustafatetik.atomcv.profile.seed.GoldenProfileReader;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.rendering.latex.LatexDocumentRenderer;
import com.mustafatetik.atomcv.rendering.measurement.TexLogParser;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * How far the budget is from the page (XI-A.3's completion checklist, Bolum
 * 23.1).
 *
 * <p>Everything else proves the arithmetic is consistent with itself: the
 * measured costs add up to the budget, the budget is not exceeded, the page
 * count comes back as one. None of that catches a systematic error — a
 * forgotten piece of furniture, a paragraph skip nobody measured — because
 * every number involved would be wrong the same way.
 *
 * <p>This asks TeX where it is on the page after the real document, and
 * compares that with what selection thought it had spent. The checklist wants
 * the two within three percent.
 */
@Tag("latex")
@Testcontainers
class MeasurementDriftIT {

    /** XI-A.3: "olcum ile gercek sayfa arasinda sapma <%3". */
    private static final double ALLOWED_DRIFT = 0.03;

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);
    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

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
    void whatSelectionSpentIsWhatThePageHolds(String name) {
        GoldenProfile golden = GoldenProfileReader.read(name, UUID.randomUUID());
        var request = SelectionRequestBuilder.build(golden.tree(),
                TemplateCustomization.CLASSIC, CAPACITY, 1,
                golden.profile().getSourceLanguage(), Tone.FORMAL, TODAY).request();
        SelectionState state = SelectionPhase.select(request).orElseThrow();

        double predictedPt = state.budget().fixedPt() + state.budget().usedPt();
        double actualPt = heightOnThePage(golden, state);

        double drift = Math.abs(actualPt - predictedPt) / predictedPt;
        assertThat(drift)
                .as("%s: predicted %.1fpt, the page holds %.1fpt", name, predictedPt, actualPt)
                .isLessThan(ALLOWED_DRIFT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void theRealDocumentNeverRunsPastThePage(String name) {
        GoldenProfile golden = GoldenProfileReader.read(name, UUID.randomUUID());
        var request = SelectionRequestBuilder.build(golden.tree(),
                TemplateCustomization.CLASSIC, CAPACITY, 1,
                golden.profile().getSourceLanguage(), Tone.FORMAL, TODAY).request();
        SelectionState state = SelectionPhase.select(request).orElseThrow();

        var client = compiler();
        String source = new LatexDocumentRenderer().renderFinal(RenderPhase.build(
                golden.profile(), golden.tree(), state,
                TemplateCustomization.CLASSIC, Locale.ENGLISH)).value();

        assertThat(client.compile(source).pageCount())
                .as("%s fills one page and not two", name)
                .isEqualTo(1);
    }

    /**
     * TeX's own answer to "how tall is what you have put on this page".
     *
     * <p>The probe is appended to the document the renderer produced rather
     * than rendered differently: a drift measured on a different document
     * would be a drift in the probe.
     */
    private double heightOnThePage(GoldenProfile golden, SelectionState state) {
        String source = new LatexDocumentRenderer().renderFinal(RenderPhase.build(
                        golden.profile(), golden.tree(), state,
                        TemplateCustomization.CLASSIC, Locale.ENGLISH)).value();

        String probed = source.replace("\\end{document}",
                "\\par\\typeout{CALIB|pagetotal|\\the\\pagetotal}\n\\end{document}");

        Map<String, Double> probes = TexLogParser.parseCalibration(compiler().measure(probed));
        assertThat(probes).as("the document has to compile for its height to mean anything")
                .containsKey("pagetotal");
        return probes.get("pagetotal");
    }

    private static LatexCompilerClient compiler() {
        return new LatexCompilerClient(new CompilationProperties(
                "http://" + LATEX.getHost() + ":" + LATEX.getMappedPort(8090),
                Duration.ofSeconds(120)));
    }
}
