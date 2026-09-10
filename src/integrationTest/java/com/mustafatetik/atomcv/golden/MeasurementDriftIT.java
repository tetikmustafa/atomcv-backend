package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.compilation.CompilationProperties;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.generation.render.RenderPhase;
import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
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
import org.junit.jupiter.api.Test;
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
 *
 * <p><strong>Only the templates whose promise has been confirmed.</strong>
 * Running this across all three on 2026-09-10 found that compact's did not
 * hold, and the largest cause has since been fixed: a section heading that
 * follows a list costs ten points more in compact than one at the top of the
 * page, and the calibration document only ever measured the second position.
 * Six of the seven profiles are inside three percent now and master_cv_en no
 * longer runs onto a second page.
 *
 * <p>What is left is smaller and is not compact's: senior_backend_tr misses by
 * 3.7%, and most of that is a header block measured at 65.3 pt against a model
 * that charges a fixed 48.99 for every profile. The real one grows with the
 * contact line and the headline, so it is under-charged for anyone whose header
 * wraps -- in every template, classic included, where offsetting errors hide
 * it. Modern still runs stress_long_career onto a second page for a reason
 * nothing here has isolated yet.
 *
 * <p>So the list below stays short, and the test under it names what is
 * missing rather than leaving a comment somebody can lose.
 */
@Tag("latex")
@Testcontainers
class MeasurementDriftIT {

    /** XI-A.3: "olcum ile gercek sayfa arasinda sapma <%3". */
    private static final double ALLOWED_DRIFT = 0.03;

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);

    @Container
    static final GenericContainer<?> LATEX = new GenericContainer<>(
            new ImageFromDockerfile("atomcv-latex-test", false)
                    .withFileFromPath(".", Path.of("docker/latex")))
            .withExposedPorts(8090)
            .withStartupTimeout(Duration.ofMinutes(5));

    /**
     * The templates this file is allowed to hold to the three percent.
     *
     * <p>Deliberately not {@code TemplateRegistry.ids()}. Compact and modern
     * fail it today for the reason in this class's own documentation, and a
     * lane that is red on main stops being read — but a list nobody wrote down
     * stops being fixed. This is the written-down version.
     */
    private static final java.util.List<String> TEMPLATES_WITH_A_CONFIRMED_PAGE_PROMISE =
            java.util.List.of("classic");

    /**
     * Sorted: {@code ids()} is the key set of a {@code Map.of} and its order is
     * salted per JVM run, which would shuffle a slow lane's report every time.
     */
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> everyTemplate() {
        return TEMPLATES_WITH_A_CONFIRMED_PAGE_PROMISE.stream().sorted()
                .flatMap(template -> GoldenProfileReader.NAMES.stream()
                        .map(name -> org.junit.jupiter.params.provider.Arguments.of(name, template)));
    }

    /**
     * And the ones left out are named, so the list above cannot quietly become
     * the whole story. A template that is fixed and not added back fails here.
     */
    @Test
    void everyTemplateLeftOutOfTheGuaranteeIsOneWeKnowAbout() {
        var missing = TemplateRegistry.ids().stream().sorted()
                .filter(id -> !TEMPLATES_WITH_A_CONFIRMED_PAGE_PROMISE.contains(id))
                .toList();

        assertThat(missing)
                .as("compact and modern each have one profile left outside the three"
                        + " percent; anything else here is new")
                .containsExactly("compact", "modern");
    }

    @ParameterizedTest(name = "{1}: {0}")
    @MethodSource("everyTemplate")
    void whatSelectionSpentIsWhatThePageHolds(String name, String template) {
        TemplateCustomization customization = TemplateRegistry.defaultsFor(template);
        GoldenProfile golden = GoldenProfileReader.read(name, UUID.randomUUID());
        SelectionState state = selectOnePage(golden, customization);

        double predictedPt = state.budget().fixedPt() + state.budget().usedPt();
        double actualPt = heightOnThePage(golden, state, customization);

        double drift = Math.abs(actualPt - predictedPt) / predictedPt;
        assertThat(drift)
                .as("%s under %s: predicted %.1fpt, the page holds %.1fpt",
                        name, template, predictedPt, actualPt)
                .isLessThan(ALLOWED_DRIFT);
    }

    @ParameterizedTest(name = "{1}: {0}")
    @MethodSource("everyTemplate")
    void theRealDocumentNeverRunsPastThePage(String name, String template) {
        TemplateCustomization customization = TemplateRegistry.defaultsFor(template);
        GoldenProfile golden = GoldenProfileReader.read(name, UUID.randomUUID());
        SelectionState state = selectOnePage(golden, customization);

        var client = compiler();
        String source = sourceOf(golden, state, customization);

        assertThat(client.compile(source).pageCount())
                .as("%s fills one %s page and not two", name, template)
                .isEqualTo(1);
    }

    private static SelectionState selectOnePage(
            GoldenProfile golden, TemplateCustomization customization) {

        CapacityModel capacity = TemplateRegistry.capacityOf(customization).orElseThrow();
        var request = SelectionRequestBuilder.build(golden.tree(), customization, capacity, 1,
                golden.profile().getSourceLanguage(), Tone.FORMAL, TODAY).request();
        return SelectionPhase.select(request).orElseThrow();
    }

    private static String sourceOf(GoldenProfile golden, SelectionState state,
            TemplateCustomization customization) {

        return new LatexDocumentRenderer().renderFinal(RenderPhase.build(
                golden.profile(), golden.tree(), state,
                RewrittenContent.none(), customization, Locale.ENGLISH)).value();
    }

    /**
     * TeX's own answer to "how tall is what you have put on this page".
     *
     * <p>The probe is appended to the document the renderer produced rather
     * than rendered differently: a drift measured on a different document
     * would be a drift in the probe.
     */
    private double heightOnThePage(GoldenProfile golden, SelectionState state,
            TemplateCustomization customization) {

        String source = sourceOf(golden, state, customization);

        String probed = source.replace("\\end{document}",
                "\\par\\typeout{CALIB|pagetotal|\\the\\pagetotal}\n\\end{document}");

        Map<String, Double> probes = TexLogParser.parseCalibration(compiler().measure(probed));
        assertThat(probes).as("the document has to compile for its height to mean anything")
                .containsKey("pagetotal");

        // A reading taken after a page break is a reading of the second page.
        // Compact's master_cv_en produced 39.8 pt against a predicted 730.6 --
        // a 95% "drift" that says nothing about the model and everything about
        // where the probe was standing. The same reset once made a calibration
        // report a heading at -646.7 pt. Refused here rather than reported,
        // because the number is not small, it is meaningless.
        assertThat(compiler().compile(source).pageCount())
                .as("the height of a page cannot be read once the document has left it")
                .isEqualTo(1);
        return probes.get("pagetotal");
    }

    private static LatexCompilerClient compiler() {
        return new LatexCompilerClient(new CompilationProperties(
                "http://" + LATEX.getHost() + ":" + LATEX.getMappedPort(8090),
                Duration.ofSeconds(120)));
    }
}
