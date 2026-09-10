package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.compilation.CompilationProperties;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.seed.GoldenProfileReader;
import com.mustafatetik.atomcv.rendering.latex.LatexDocumentRenderer;
import com.mustafatetik.atomcv.rendering.latex.LatexInlineRenderer;
import com.mustafatetik.atomcv.rendering.latex.PreambleBuilder;
import com.mustafatetik.atomcv.rendering.measurement.MeasurementService;
import com.mustafatetik.atomcv.rendering.measurement.TexLogParser;
import com.mustafatetik.atomcv.rendering.model.MeasurementRequest;
import com.mustafatetik.atomcv.rendering.template.CapacityModel.RowShape;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * What a wording is charged is what the page pays for it (Bolum 22.4).
 *
 * <p><strong>The one assertion the drift test cannot make.</strong> Drift
 * compares a whole document against a whole budget, so an error of one line in
 * one bullet disappears into seven hundred points. This adds a single bullet to
 * a list twice over and takes the difference, which is the page's own price for
 * that wording and nothing else.
 *
 * <p>It exists because that price was wrong for two years' worth of templates
 * and nothing said so. {@code \resumeItem} left an interword space between the
 * wording and the negative {@code \vspace} after it; the measurement boxed the
 * wording alone, so a bullet whose natural width landed within one space of the
 * line measured at one line and was set at two. Sixty of them made a one-page
 * promise a two-page PDF.
 *
 * <p>The wording is stress_long_career's, deliberately: it was written to sit
 * at classic's line boundary, which is exactly where a missing space decides
 * the answer. A wording safely short of the line would pass this test under any
 * macro at all.
 */
@Tag("latex")
@Testcontainers
class WordingCostIT {

    private static final String BS = String.valueOf((char) 92);
    private static final char NL = (char) 10;

    /** Two sums of measured points, so not equality — see GoldenCostsIT. */
    private static final Offset<Double> A_POINT = Offset.offset(0.01);

    @Container
    static final GenericContainer<?> LATEX = new GenericContainer<>(
            new ImageFromDockerfile("atomcv-latex-test", false)
                    .withFileFromPath(".", Path.of("docker/latex")))
            .withExposedPorts(8090)
            .withStartupTimeout(Duration.ofMinutes(5));

    static List<String> templates() {
        return TemplateRegistry.ids().stream().sorted().toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("templates")
    void themarginalCostOfAbulletIsWhatItMeasured(String template) {
        TemplateCustomization customization = TemplateRegistry.defaultsFor(template);
        var capacity = TemplateRegistry.capacityOf(customization).orElseThrow();
        AtomVariant variant = atTheLineBoundary();

        var client = new LatexCompilerClient(new CompilationProperties(
                "http://" + LATEX.getHost() + ":" + LATEX.getMappedPort(8090),
                Duration.ofSeconds(120)));
        var measurements = new MeasurementService(new LatexDocumentRenderer(), client);

        double measured = measurements.measure(new MeasurementRequest(
                        List.of(new MeasurementRequest.MeasurableItem(
                                "one", variant.getContent(), RowShape.ENTRY_BULLET)),
                        customization))
                .get("one")
                .totalPt(capacity.itemBaselineSkipPt(),
                        capacity.rowSpacingPt(RowShape.ENTRY_BULLET));

        double onThePage = heightOf(client, customization, variant, 3)
                - heightOf(client, customization, variant, 2);

        assertThat(onThePage)
                .as("%s sets this bullet at %.2f pt and charges %.2f", template, onThePage, measured)
                .isCloseTo(measured, A_POINT);
    }

    /**
     * The tallest wording of the fixture that was written to sit at the line's
     * edge. Not a string here, because a string here would stop being the
     * shape the golden set actually carries.
     */
    private static AtomVariant atTheLineBoundary() {
        return GoldenProfileReader.read("stress_long_career", UUID.randomUUID()).variants().stream()
                .max(java.util.Comparator.comparingInt(v -> v.getPlainText().length()))
                .orElseThrow();
    }

    /** How far down the page a list of {@code count} of this bullet reaches. */
    private static double heightOf(LatexCompilerClient client,
            TemplateCustomization customization, AtomVariant variant, int count) {

        String text = LatexInlineRenderer.render(variant.getContent());
        var body = new StringBuilder();
        body.append(BS).append("begin{document}").append(NL)
                .append(BS).append("resumeSubHeadingListStart").append(NL)
                .append(BS).append("resumeSubheading{A}{B}{C}{D}").append(NL)
                .append(BS).append("resumeItemListStart").append(NL);
        for (int i = 0; i < count; i++) {
            body.append(BS).append("resumeItem{").append(text).append("}").append(NL);
        }
        body.append(BS).append("par").append(BS).append("typeout{CALIB|at|")
                .append(BS).append("the").append(BS).append("pagetotal}").append(NL)
                .append(BS).append("resumeItemListEnd").append(NL)
                .append(BS).append("resumeSubHeadingListEnd").append(NL)
                .append(BS).append("end{document}").append(NL);

        Map<String, Double> probes = TexLogParser.parseCalibration(
                client.measure(PreambleBuilder.build(customization) + body));
        assertThat(probes)
                .as("the probe document has to compile for its height to mean anything")
                .containsKey("at");
        return probes.get("at");
    }
}
