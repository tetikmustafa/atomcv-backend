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
 * so the first one cancels and is never looked at. This looks at it, and it is
 * where modern's last drift was found and fixed on 2026-09-11: a wording at the
 * line's edge was set on two lines as the <em>last</em> bullet of a list and on
 * one anywhere else, so every list was charged a line short of what it printed.
 *
 * <p>The cause was a space, again. {@code \resumeItem} was written across three
 * lines, and every line break inside a macro body is a space -- so the page had
 * to fit the wording plus a space the measurement never boxed. The premium here
 * fell from 17.05 to 5.50 when the body was closed up, and modern joined the
 * templates whose page promise is confirmed.
 *
 * <p>What is left is the list's own overhead, which is what this number is for:
 * 0.55 in classic, -5.10 in compact, 2.55 in modern, plus about three points
 * that a long last line leaves behind and a following bullet absorbs. Compact,
 * whose {@code \resumeItem} has no {@code \vspace} at all, keeps only half a
 * point of it -- so that residue is the negative space being discarded at the
 * end of a list rather than spent.
 *
 * <p><strong>The premium is asserted rather than tolerated.</strong> Each
 * template is held to what it measures at today, which is how this test noticed
 * its own subject being fixed: the number moved and the lane went red.
 */
@Tag("latex")
@Testcontainers
class EntryFurnitureIT {

    private static final String BS = String.valueOf((char) 92);
    private static final char NL = (char) 10;

    /**
     * What the first bullet of a list costs above a later one, measured
     * 2026-09-11 with the golden set's boundary wording.
     *
     * <p>Most of it is furniture and is meant to be there: opening the list is
     * charged {@code ITEMIZE_OVERHEAD}, 0.55 in classic, -5.10 in compact and
     * 2.55 in modern. What is left over -- 2.95, 2.64 and 2.95 --
     * <strong>is not a cost anybody pays, and chasing it is wasted</strong>.
     *
     * <p>That was measured on 2026-09-11 rather than argued. The marginal cost
     * of one more entry and its list is <em>exactly</em> what the model charges
     * in all three templates -- 43.72, 25.47 and 51.72, to the hundredth, at
     * two entries and at three and at four, with a section heading after the
     * list and without one. There is no per-entry error left to find.
     *
     * <p>The leftover appears here because of how this test asks. It compares a
     * document that has a list with one that has none, so the only thing
     * standing behind the list is the probe itself, and what the probe reads is
     * space that a following entry absorbs and that TeX discards at the end of
     * a page. The number is worth pinning because it moves when the templates
     * move; it is not worth fixing.
     */
    private static final Map<String, Double> FIRST_BULLET_PREMIUM_PT = Map.of(
            "classic", 3.50,
            "compact", -2.46,
            "modern", 5.50);

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
