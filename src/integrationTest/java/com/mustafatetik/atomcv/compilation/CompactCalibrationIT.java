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
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Compact's stored capacity, re-derived from the compiler (Bolum 26.4, 33.5).
 *
 * <p>What {@code LatexCalibrationIT} is for classic. Separate rather than
 * shared, and the reason is not laziness: that class reads every number
 * through a static field measured once in {@code @BeforeAll}, and two of its
 * assertions are invariants of classic's furniture rather than of every
 * template — a bullet in a list of its own costs more than one nested under an
 * entry, which is true of a list that pads itself and false of one opened with
 * {@code nosep}. Parameterising it would have meant rewriting twenty methods
 * to weaken two of them.
 *
 * <p>So this asserts the same seventeen numbers in the other shape: compute
 * the whole capacity from the probes, then compare it to what is stored. When
 * the preamble changes these fail together, which is the signal to re-measure
 * and raise the template version rather than let stored costs describe a
 * document that no longer exists.
 */
@Tag("latex")
@Testcontainers
class CompactCalibrationIT {

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
                .renderCalibration(TemplateCustomization.COMPACT);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + LATEX.getHost() + ":"
                        + LATEX.getMappedPort(8090) + "/measure"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(source.value(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> response =
                CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        probes = TexLogParser.parseCalibration(new String(response.body(), StandardCharsets.UTF_8));
        assertThat(probes).as("the calibration document has to compile").isNotEmpty();
    }

    // ── the page ──────────────────────────────────────────────────────────

    /**
     * A 0.4in margin against classic's 0.5in, which is 14.45pt more in each
     * direction — asserted against classic rather than only against the stored
     * number, because the arithmetic is the one part of this a reader can check
     * without a compiler.
     */
    @Test
    void thepageIsTheClassicPageWithAsmallerMargin() {
        assertThat(probes.get("textheight")).isCloseTo(capacity().pageTextHeightPt(), offset());
        assertThat(probes.get("textwidth")).isCloseTo(capacity().textWidthPt(), offset());
        assertThat(capacity().pageTextHeightPt() - classic().pageTextHeightPt())
                .isCloseTo(14.45, Offset.offset(0.01));
        assertThat(capacity().textWidthPt() - classic().textWidthPt())
                .isCloseTo(14.45, Offset.offset(0.01));
    }

    @Test
    void thetwoBaselinesMatchWhatIsStored() {
        assertThat(probes.get("baselineskip")).isCloseTo(capacity().baselineSkipPt(), offset());
        assertThat(probes.get("itembaselineskip"))
                .isCloseTo(capacity().itemBaselineSkipPt(), offset());
        assertThat(capacity().itemBaselineSkipPt()).isLessThan(capacity().baselineSkipPt());
    }

    @Test
    void thepageIsDenserThanClassic() {
        // Bolum 33.5 asks for it in lines: about sixty-four against
        // fifty-four. Read as bullet lines before furniture, that is seventy
        // against sixty.
        double compactLines = capacity().pageTextHeightPt() / capacity().itemBaselineSkipPt();
        double classicLines = classic().pageTextHeightPt() / classic().itemBaselineSkipPt();

        assertThat(compactLines).isGreaterThan(classicLines * 1.10);
    }

    // ── the furniture ─────────────────────────────────────────────────────

    @Test
    void everyStoredCostIsWhatTheCompilerSays() {
        assertThat(delta("start", "afterHeaderBlock"))
                .as("header block").isCloseTo(cost(CapacityModel.HEADER_BLOCK), offset());
        assertThat(sectionHeader())
                .as("section heading").isCloseTo(cost(CapacityModel.SECTION_HEADER), offset());
        assertThat(delta("beforeBareEntry", "afterBareEntry"))
                .as("entry heading").isCloseTo(cost(CapacityModel.ENTRY_HEADER), offset());
        assertThat(itemLine())
                .as("a bullet under an entry").isCloseTo(cost(CapacityModel.ITEM_LINE), offset());
        assertThat(itemizeOverhead())
                .as("opening a bullet list under an entry")
                .isCloseTo(cost(CapacityModel.ITEMIZE_OVERHEAD), offset());
        assertThat(sectionItemLine())
                .as("a bullet in a list of its own")
                .isCloseTo(cost(CapacityModel.SECTION_ITEM_LINE), offset());
        assertThat(sectionOne() - sectionItemLine())
                .as("opening that list")
                .isCloseTo(cost(CapacityModel.SECTION_LIST_OVERHEAD), offset());
        // The leftover a section's list puts under the heading below it, less
        // whatever that heading is already charged for standing after a list.
        // In compact those are the same ten points, so what is stored is
        // nothing -- and charging both spent them twice, which is what put
        // career_changer 10.74 pt over a real page. The subtraction is asserted
        // here rather than copied from the service: what has to hold is the
        // relationship between the two stored numbers, and this says so from
        // its own probes.
        double headingPremium =
                cost(CapacityModel.SECTION_HEADER_AFTER_LIST) - cost(CapacityModel.SECTION_HEADER);
        assertThat(delta("afterListUnderSection", "beforeThreeUnderSection")
                - sectionHeader() - headingPremium)
                .as("closing it, less what the next heading already pays for")
                .isCloseTo(cost(CapacityModel.SECTION_LIST_CLOSE), offset());
        assertThat(delta("beforeParagraphOne", "afterParagraphOne") - sectionItemLine())
                .as("opening a paragraph list")
                .isCloseTo(cost(CapacityModel.PARAGRAPH_LIST_OVERHEAD), offset());
        assertThat(inlineRow())
                .as("an inline row").isCloseTo(cost(CapacityModel.INLINE_ROW), offset());
        assertThat(inlineOne() - inlineRow())
                .as("opening an inline list")
                .isCloseTo(cost(CapacityModel.INLINE_LIST_OVERHEAD), offset());
    }

    @Test
    void asecondHeadingCostsMoreThanTheFirst() {
        double marginal = delta("beforeTwoEntries", "afterTwoEntries")
                - delta("beforeOneEntry", "afterOneEntry");

        assertThat(marginal - bulletAndItsList())
                .isCloseTo(cost(CapacityModel.ENTRY_HEADER_AFTER_LIST), offset());
        assertThat(cost(CapacityModel.ENTRY_HEADER_AFTER_LIST))
                .as("a list closing above it costs the heading more")
                .isGreaterThan(cost(CapacityModel.ENTRY_HEADER));
    }

    @Test
    void aprojectHeadingIsOneLineAndCostsLessThanAnEntryHeading() {
        assertThat(delta("beforeOneProject", "afterOneProject") - bulletAndItsList())
                .isCloseTo(cost(CapacityModel.PROJECT_HEADING), offset());
        assertThat(delta("beforeTwoProjects", "afterTwoProjects")
                        - delta("beforeOneProject", "afterOneProject") - bulletAndItsList())
                .isCloseTo(cost(CapacityModel.PROJECT_HEADING_AFTER_LIST), offset());
        assertThat(cost(CapacityModel.PROJECT_HEADING))
                .isLessThan(cost(CapacityModel.ENTRY_HEADER));
    }

    // ── what nosep changes, and the check that caught the first draft ─────

    /**
     * <strong>A bullet advances the page by exactly one small baseline.</strong>
     *
     * <p>This is the assertion the first compact preamble failed, and it failed
     * usefully: it carried classic's {@code \vspace{-4pt}} on top of
     * {@code nosep}, and the four points classic spends against the separation
     * between two items came out of the text instead — a bullet measured
     * 5.45pt against a 10.45pt line, which is one line of text drawn over
     * another. Nothing else here would have noticed; the numbers would simply
     * have been small, and every page would have been over-filled.
     */
    @Test
    void abulletCostsExactlyOneSmallLine() {
        assertThat(cost(CapacityModel.ITEM_LINE))
                .isCloseTo(capacity().itemBaselineSkipPt(), offset());
        assertThat(cost(CapacityModel.INLINE_ROW))
                .as("and so does an inline row")
                .isCloseTo(cost(CapacityModel.ITEM_LINE), offset());
    }

    /**
     * And a bullet costs the same wherever it is set, which is the one
     * invariant compact does not share with classic.
     *
     * <p>Classic charges five points more for a bullet in a list of its own,
     * because a first-level itemize separates its items. {@code nosep} takes
     * exactly that away, so the two are equal here — asserted rather than left
     * as a coincidence, because if they ever diverge the template has stopped
     * being the one these numbers were measured against.
     */
    @Test
    void nosepMakesTheTwoBulletShapesCostTheSame() {
        assertThat(cost(CapacityModel.SECTION_ITEM_LINE))
                .isCloseTo(cost(CapacityModel.ITEM_LINE), offset());
        assertThat(classic().fixedCost(CapacityModel.SECTION_ITEM_LINE))
                .as("where classic's are five points apart")
                .isGreaterThan(classic().fixedCost(CapacityModel.ITEM_LINE));
    }

    /** Layer B still refuses: compact's numbers are compact's settings only. */
    @Test
    void compactAtOtherSettingsHasNoCapacityAtAll() {
        var moved = new TemplateCustomization("compact",
                TemplateCustomization.COMPACT.fontFamily(), 11.0,
                TemplateCustomization.COMPACT.marginInches(),
                TemplateCustomization.COMPACT.lineSpacing(),
                TemplateCustomization.COMPACT.accentColor());

        assertThat(new LatexDocumentRenderer().capacity(moved)).isEmpty();
    }

    // ── derivations, the same ones LatexCalibrationIT asserts ─────────────

    private static double sectionHeader() {
        return delta("afterHeaderBlock", "afterSection");
    }

    private static double sectionOne() {
        return delta("afterSection", "afterListUnderSection");
    }

    private static double sectionItemLine() {
        return (delta("beforeThreeUnderSection", "afterThreeUnderSection") - sectionOne()) / 2;
    }

    private static double itemLine() {
        return (delta("beforeEntryThreeItems", "afterEntryThreeItems")
                - delta("beforeOneEntry", "afterOneEntry")) / 2;
    }

    private static double itemizeOverhead() {
        return delta("beforeOneEntry", "afterOneEntry")
                - delta("beforeBareEntry", "afterBareEntry") - itemLine();
    }

    private static double bulletAndItsList() {
        return cost(CapacityModel.ITEMIZE_OVERHEAD) + cost(CapacityModel.ITEM_LINE);
    }

    private static double inlineOne() {
        return delta("beforeInlineOne", "afterInlineOne");
    }

    private static double inlineRow() {
        return (delta("beforeInlineThree", "afterInlineThree") - inlineOne()) / 2;
    }

    private static double cost(String name) {
        return capacity().fixedCost(name);
    }

    private static CapacityModel capacity() {
        return new LatexDocumentRenderer().capacity(TemplateCustomization.COMPACT).orElseThrow();
    }

    private static CapacityModel classic() {
        return new LatexDocumentRenderer().capacity(TemplateCustomization.CLASSIC).orElseThrow();
    }

    private static double delta(String from, String to) {
        return probes.get(to) - probes.get(from);
    }

    private static Offset<Double> offset() {
        return Offset.offset(TOLERANCE_PT);
    }
}
