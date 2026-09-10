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
import com.mustafatetik.atomcv.profile.domain.Profile;
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
 * <p><strong>All three templates hold it now</strong>, on all seven golden
 * profiles. Getting there took five fixes, and each was the same mistake in a
 * different place: <em>something the page sets that the measurement never saw.</em>
 *
 * <ul>
 * <li>A section heading after a list costs ten points more in compact, and the
 * calibration only ever measured the other position.</li>
 * <li>The header block was one constant per template; it is text, it wraps, and
 * one profile's measures 65.2 pt against a charge of 48.99.</li>
 * <li>{@code
esumeItem} left an interword space between the wording and the
 * negative {@code space} after it.</li>
 * <li>The same macro was written across three lines, and every line break
 * inside a macro body is another space.</li>
 * <li>Compact charged one gap twice, as the list's leftover and again as the
 * heading's premium.</li>
 * </ul>
 *
 * <p>The two space bugs are worth reading together. Either one puts a bullet
 * whose natural width lands within a space of the line onto a second line, and
 * only where it is the last of its list -- which is why it took a fixture of
 * sixty such bullets to make either visible, and why the drift test alone could
 * never have named them. {@code WordingCostIT} and {@code EntryFurnitureIT}
 * measure a bullet and an entry against what the page pays for them, which is
 * where a single line's error is still a whole line rather than 2% of a page.
 *
 * <p>The list below is therefore every shipped template, and the test under it
 * asserts that nothing is missing from it rather than leaving a comment
 * somebody can lose.
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
            java.util.List.of("classic", "compact", "modern");

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
                .as("every shipped template is held to the three percent")
                .isEmpty();
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
                golden.profile().getSourceLanguage(), Tone.FORMAL, TODAY,
                // The header this profile measured, not the template's constant
                // for every profile. English, because that is what renderFinal
                // prints below.
                golden.profile().getHeaderCosts().get(
                        Profile.headerKey(customization.costKey(), "en"))).request();
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
