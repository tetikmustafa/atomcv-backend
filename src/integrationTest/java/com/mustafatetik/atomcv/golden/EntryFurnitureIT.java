package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.compilation.CompilationProperties;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.rendering.latex.PreambleBuilder;
import com.mustafatetik.atomcv.rendering.measurement.TexLogParser;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * What the first bullet of a list costs, against what a later one costs.
 *
 * <p>{@code WordingCostIT} compares the second bullet of a list with the third,
 * so the first one cancels and is never looked at. This looks at it, and modern
 * is where that matters: a wording at the line's edge is set on two lines as
 * the first bullet under an entry heading and on one as any later bullet. The
 * measurement measures a bullet on its own, so it reports the later one, and
 * every list in the document is charged a line short of what it prints.
 *
 * <p>That is the whole of modern's remaining drift. With a bullet reading
 * "Probe" -- a word that could not wrap at any width -- all three templates
 * spend exactly what they are charged, to fifteen decimal places. It appears
 * only with a wording that sits in the band where one line's worth of width
 * decides the answer, which is the same band the interword space decided.
 *
 * <p><strong>The difference is asserted rather than tolerated.</strong> A test
 * that only checked classic and compact would go on passing while modern stayed
 * wrong, and a comment would go on being read after it stopped being true. Each
 * template is held to what it measures at today, so the day this is fixed, this
 * is what says the number moved.
 */
@Tag("latex")
@Testcontainers
class EntryFurnitureIT {

    private static final String BS = String.valueOf((char) 92);
    private static final char NL = (char) 10;

    /**
     * What the first bullet of a list costs above a later one, measured
     * 2026-09-10 with the golden set's boundary wording.
     *
     * <p>Some of this is furniture and is meant to be there: opening the list
     * is charged {@code ITEMIZE_OVERHEAD}, which is 0.55 in classic, -5.10 in
     * compact and 2.55 in modern. Take that away and classic keeps 2.95,
     * compact 2.64 -- an entry list's close, which nothing charges for -- and
     * <strong>modern keeps 14.50, which is those same points and a whole
     * line.</strong> That line is the defect: the same wording is set on two
     * lines here and on one further down the list.
     */
    private static final Map<String, Double> FIRST_BULLET_PREMIUM_PT = Map.of(
            "classic", 3.50,
            "compact", -2.46,
            "modern", 17.05);

    private static final Offset<Double> A_TENTH = Offset.offset(0.1);

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
    void thefirstBulletOfAlistCostsWhatItAlwaysHas(String template) {
        TemplateCustomization customization = TemplateRegistry.defaultsFor(template);
        var client = new LatexCompilerClient(new CompilationProperties(
                "http://" + LATEX.getHost() + ":" + LATEX.getMappedPort(8090),
                Duration.ofSeconds(120)));

        var boundary = com.mustafatetik.atomcv.rendering.latex.LatexInlineRenderer.render(
                com.mustafatetik.atomcv.profile.seed.GoldenProfileReader
                        .read("stress_long_career", java.util.UUID.randomUUID())
                        .variants().stream()
                        .max(java.util.Comparator.comparingInt(v -> v.getPlainText().length()))
                        .orElseThrow().getContent());

        // Two lists of the same bullet, one item apart, and the same two with
        // one item more. The first difference includes the opening bullet; the
        // second is a bullet in the middle of a list. On a page that sets both
        // the same, they are equal.
        double first = heightOf(client, customization, boundary, 1)
                - heightOf(client, customization, boundary, 0);
        double later = heightOf(client, customization, boundary, 3)
                - heightOf(client, customization, boundary, 2);

        assertThat(first - later)
                .as("%s: the first bullet of a list costs %.2f and a later one %.2f",
                        template, first, later)
                .isCloseTo(FIRST_BULLET_PREMIUM_PT.get(template), A_TENTH);
    }

    /** And every template is named above, so a fourth one cannot arrive unmeasured. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("templates")
    void everyTemplateHasAmeasuredPremium(String template) {
        assertThat(FIRST_BULLET_PREMIUM_PT).containsKey(template);
    }

    /** One entry whose list holds {@code bullets} of the same wording. */
    private static double heightOf(LatexCompilerClient client,
            TemplateCustomization customization, String wording, int bullets) {

        var body = new StringBuilder();
        body.append(BS).append("begin{document}").append(NL)
                .append(BS).append("section*{Probe}").append(NL)
                .append(BS).append("resumeSubHeadingListStart").append(NL)
                .append(BS).append("resumeSubheading{Probe}{Probe}{Probe}{Probe}").append(NL);
        if (bullets > 0) {
            body.append(BS).append("resumeItemListStart").append(NL);
            for (int bullet = 0; bullet < bullets; bullet++) {
                body.append(BS).append("resumeItem{").append(wording).append("}").append(NL);
            }
            body.append(BS).append("resumeItemListEnd").append(NL);
        }
        body.append(BS).append("par").append(BS).append("typeout{CALIB|at|")
                .append(BS).append("the").append(BS).append("pagetotal}").append(NL)
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
