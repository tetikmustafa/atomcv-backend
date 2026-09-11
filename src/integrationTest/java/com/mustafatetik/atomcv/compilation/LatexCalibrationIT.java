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
 *
 * <p><strong>Every probe is nested the way the renderer nests it.</strong> A
 * second entry is measured inside a sub-heading list that is already open,
 * because that is where the page puts it. Measuring it in a list of its own —
 * which this document did while {@code \resumeSubHeadingListStart} expanded to
 * nothing and the difference was zero — charges every entry after the first for
 * a list it never opens. It came to eighty-seven points on a real page, and
 * nothing here failed, because every number involved described the same wrong
 * document. {@code MeasurementDriftIT} is what caught it.
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

        HttpResponse<byte[]> response =
                CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        probes = TexLogParser.parseCalibration(new String(response.body(), StandardCharsets.UTF_8));
        assertThat(probes).as("the calibration document has to compile").isNotEmpty();
    }

    // ── the page ──────────────────────────────────────────────────────────

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

    /**
     * The document has two baselines, and both are stored.
     *
     * <p>The reference template sets every bullet {@code \small}, so a bullet
     * advances the page by less than a paragraph does. Reading a measured box
     * of small lines back against the page's own baseline is not a rounding
     * error that stays small — nine small lines divided by the large baseline
     * comes back as eight, and the ninth is not paid for.
     */
    @Test
    void abulletHasItsOwnBaselineAndItIsSmaller() {
        assertThat(probes.get("itembaselineskip"))
                .isCloseTo(capacity().itemBaselineSkipPt(), offset());
        assertThat(capacity().itemBaselineSkipPt())
                .isLessThan(capacity().baselineSkipPt());
    }

    @Test
    void theHeaderBlockCostsWhatWasMeasured() {
        assertThat(delta("start", "afterHeaderBlock"))
                .isCloseTo(capacity().fixedCost(CapacityModel.HEADER_BLOCK), offset());
    }

    // ── section headings ──────────────────────────────────────────────────

    @Test
    void asectionHeadingCostsWhatWasMeasured() {
        assertThat(delta("afterHeaderBlock", "afterSection"))
                .isCloseTo(capacity().fixedCost(CapacityModel.SECTION_HEADER), offset());
    }

    /**
     * And the same number wherever it is on the page.
     *
     * <p>It looked as though it had to be two: the reference opens its section
     * format with a negative space, so what closed above a heading ought to
     * decide how much of that is spent. Measured with the paragraph flushed
     * first — which is how the real page is read — they are identical to five
     * decimals. The negative space is spent against the heading's own spacing.
     */
    @Test
    void asectionHeadingCostsTheSameAfterAsectionOfEntries() {
        assertThat(delta("beforeSectionAfterList", "afterSectionAfterList"))
                .isCloseTo(capacity().fixedCost(CapacityModel.SECTION_HEADER), offset());
    }

    /**
     * <strong>Except after a list of loose bullets, where it costs a whole
     * small line more</strong> — and that line is the reason a real profile's
     * page came out four percent over what selection thought it had spent.
     *
     * <p>{@code \resumeItemListEnd} closes with {@code \vspace{-5pt}}; the
     * heading below opens with {@code \addvspace}, which takes the larger of
     * what is asked for and what is already there. The negative pull is thrown
     * away and the {@code \topsep} above it is kept. Measured at one bullet and
     * at three, so it is the list's end rather than anything about its length.
     *
     * <p>Charged to the list rather than to this heading, which is where
     * {@code CapacityModel.SECTION_LIST_CLOSE} says why.
     */
    @Test
    void asectionHeadingAfterAlistOfLooseBulletsCostsAlineMore() {
        double base = capacity().fixedCost(CapacityModel.SECTION_HEADER);
        double close = capacity().fixedCost(CapacityModel.SECTION_LIST_CLOSE);
        // Zero in classic, where a heading costs the same in both positions.
        // Compact is where it is not, and where charging the leftover and the
        // premium both spent the same ten points twice.
        double premium =
                capacity().fixedCost(CapacityModel.SECTION_HEADER_AFTER_LIST) - base;

        assertThat(delta("afterListUnderSection", "beforeThreeUnderSection") - base - premium)
                .as("after one loose bullet")
                .isCloseTo(close, offset());
        assertThat(delta("afterThreeUnderSection", "beforeBareEntry") - base - premium)
                .as("after three, so it is the end of the list and not its length")
                .isCloseTo(close, offset());
        assertThat(close)
                .as("one small baseline, which is a bullet")
                .isCloseTo(capacity().itemBaselineSkipPt(), offset());
    }

    /**
     * And the other two label-less lists leave nothing behind them, because
     * neither closes with the negative space a bullet list does.
     */
    @Test
    void aparagraphOrAninlineListLeavesTheHeadingBelowItAlone() {
        double base = capacity().fixedCost(CapacityModel.SECTION_HEADER);

        assertThat(delta("afterParagraphOne", "beforeInlineOne"))
                .as("after a summary")
                .isCloseTo(base, offset());
        assertThat(delta("afterInlineOne", "beforeInlineThree"))
                .as("after a skills matrix")
                .isCloseTo(base, offset());
        assertThat(capacity().sectionListClosePt(
                com.mustafatetik.atomcv.profile.domain.SectionLayout.PARAGRAPH))
                .isZero();
        assertThat(capacity().sectionListClosePt(
                com.mustafatetik.atomcv.profile.domain.SectionLayout.INLINE_LIST))
                .isZero();
    }

    // ── bullets and the lists they sit in ─────────────────────────────────

    /**
     * A list opening straight under a section heading, with no entry between
     * them — a skills matrix. It is not the same number as a list under an
     * entry heading, for the reason {@code ENTRY_HEADER_AFTER_LIST} is not the
     * same as {@code ENTRY_HEADER}: TeX takes the larger of the space asked
     * for and the space already there, and a section heading has just left
     * some behind.
     */
    @Test
    void alistUnderASectionHeadingSeparatesIntoOverheadAndLines() {
        double one = delta("afterSection", "afterListUnderSection");
        double three = delta("beforeThreeUnderSection", "afterThreeUnderSection");
        double perItem = (three - one) / 2;

        assertThat(perItem)
                .as("a bullet in a first-level list, which is not the one under an entry")
                .isCloseTo(capacity().fixedCost(CapacityModel.SECTION_ITEM_LINE), offset());
        assertThat(one - perItem)
                .isCloseTo(capacity().fixedCost(CapacityModel.SECTION_LIST_OVERHEAD), offset());
    }

    /**
     * A marginal bullet is a small baseline plus what the list sets between two
     * items, less the four points {@code \resumeItem} pulls back after itself.
     */
    @Test
    void abulletUnderAnEntryCostsLessThanOneUnderAsectionHeading() {
        assertThat(capacity().rowSpacingPt(CapacityModel.RowShape.ENTRY_BULLET))
                .as("second level: a bullet is a baseline and nothing more")
                .isCloseTo(0.0, offset());
        assertThat(capacity().rowSpacingPt(CapacityModel.RowShape.SECTION_BULLET))
                .as("first level: the separation an itemize sets is still there")
                .isGreaterThan(0.0);
        assertThat(capacity().fixedCost(CapacityModel.ITEM_LINE))
                .isLessThan(capacity().fixedCost(CapacityModel.SECTION_ITEM_LINE));
    }

    // ── entries, inside the one list a section opens for them ─────────────

    /**
     * An entry with nothing under it — a degree line — is the heading and the
     * sub-heading list it opens, and nothing else (Bolum 20.2).
     *
     * <p>Every other entry number is derived from this one, so it is measured
     * on its own rather than backed out of a block that also holds a list.
     */
    @Test
    void anentryHeadingCostsWhatWasMeasured() {
        assertThat(delta("beforeBareEntry", "afterBareEntry"))
                .isCloseTo(capacity().fixedCost(CapacityModel.ENTRY_HEADER), offset());
    }

    /**
     * A bullet list under an entry heading, separated into what it costs to
     * open and what each bullet costs.
     *
     * <p><strong>Measured inside an entry, not under a section heading.</strong>
     * The two are not the same list — TeX adds the space above one with
     * {@code \addvspace} and a section heading has just left some behind —
     * and while this was derived from the section-level probe a real page came
     * out two pages long. A bullet that is charged a tenth of a point light is
     * six points a page on a CV of sixty.
     */
    @Test
    void abulletListUnderAnEntrySeparatesIntoOverheadAndLines() {
        assertThat(perBulletUnderAnEntry())
                .isCloseTo(capacity().fixedCost(CapacityModel.ITEM_LINE), offset());
        assertThat(delta("beforeOneEntry", "afterOneEntry")
                        - capacity().fixedCost(CapacityModel.ENTRY_HEADER)
                        - perBulletUnderAnEntry())
                .isCloseTo(capacity().fixedCost(CapacityModel.ITEMIZE_OVERHEAD), offset());
    }

    @Test
    void asecondEntryCostsTheHeadingAndNotTheListAgain() {
        double marginal = delta("beforeTwoEntries", "afterTwoEntries")
                - delta("beforeOneEntry", "afterOneEntry");

        assertThat(marginal - bulletAndItsList())
                .isCloseTo(capacity().fixedCost(CapacityModel.ENTRY_HEADER_AFTER_LIST), offset());
        assertThat(capacity().fixedCost(CapacityModel.ENTRY_HEADER_AFTER_LIST))
                .as("the paragraph skip above it is still to be paid")
                .isGreaterThan(capacity().fixedCost(CapacityModel.ENTRY_HEADER));
    }

    /**
     * A project is an entry carrying no employer, no place and no dates, so the
     * renderer gives it a one-line heading rather than a two-line one — and it
     * is cheaper by about a line.
     *
     * <p>It had no constant of its own while every entry was charged the
     * two-line number. On a page with two projects that is most of a bullet
     * given away.
     */
    @Test
    void aprojectHeadingIsOneLineAndCostsLessThanAnEntryHeading() {
        assertThat(delta("beforeOneProject", "afterOneProject") - bulletAndItsList())
                .isCloseTo(capacity().fixedCost(CapacityModel.PROJECT_HEADING), offset());
        assertThat(capacity().fixedCost(CapacityModel.PROJECT_HEADING))
                .isLessThan(capacity().fixedCost(CapacityModel.ENTRY_HEADER));
    }

    @Test
    void asecondProjectHeadingCostsWhatWasMeasured() {
        double marginal = delta("beforeTwoProjects", "afterTwoProjects")
                - delta("beforeOneProject", "afterOneProject");

        assertThat(marginal - bulletAndItsList())
                .isCloseTo(capacity().fixedCost(CapacityModel.PROJECT_HEADING_AFTER_LIST),
                        offset());
    }

    // ── the two label-less layouts ────────────────────────────────────────

    /**
     * A summary opens the same label-less list a Tech Stack does, so it opens
     * for the same number — and its contents are {@code \resumeItem}s, so they
     * cost what a bullet costs.
     */
    @Test
    void aparagraphListOpensLikeAbulletListAndHoldsBullets() {
        assertThat(delta("beforeParagraphOne", "afterParagraphOne")
                        - capacity().fixedCost(CapacityModel.SECTION_ITEM_LINE))
                .isCloseTo(capacity().fixedCost(CapacityModel.PARAGRAPH_LIST_OVERHEAD), offset());
        assertThat(capacity().fixedCost(CapacityModel.PARAGRAPH_LIST_OVERHEAD))
                .as("five points dearer than a bullet list, which closes by pulling back")
                .isGreaterThan(capacity().fixedCost(CapacityModel.SECTION_LIST_OVERHEAD));
    }

    /**
     * <strong>An inline list is its own list, and a row in it is its own
     * line.</strong> {@code \resumeInlineList} is one {@code itemize} holding a
     * single {@code \item} whose rows are separated by {@code \\}, so a row
     * costs a baseline and nothing else, where a bullet also pays the
     * separation an itemize sets between two items.
     *
     * <p>The two shared one constant while the template zeroed every list
     * length and set nothing {@code \small}. Both of those went with the
     * reference's own spacing, and this said so on the first run.
     */
    @Test
    void aninlineListIsItsOwnListWithItsOwnRow() {
        double one = delta("beforeInlineOne", "afterInlineOne");
        double three = delta("beforeInlineThree", "afterInlineThree");
        double perRow = (three - one) / 2;

        assertThat(perRow)
                .as("a row is one small baseline and nothing else")
                .isCloseTo(capacity().fixedCost(CapacityModel.INLINE_ROW), offset());
        assertThat(one - perRow)
                .isCloseTo(capacity().fixedCost(CapacityModel.INLINE_LIST_OVERHEAD), offset());
        assertThat(perRow)
                .as("a row is a bare baseline, like a bullet nested under an entry")
                .isCloseTo(capacity().fixedCost(CapacityModel.ITEM_LINE), offset());
        assertThat(perRow)
                .as("and cheaper than a bullet in a list of its own")
                .isLessThan(capacity().fixedCost(CapacityModel.SECTION_ITEM_LINE));
        assertThat(capacity().rowSpacingPt(CapacityModel.RowShape.INLINE_ROW_SHAPE))
                .as("nothing at all is set between two rows")
                .isCloseTo(0.0, offset());
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

    /** What every entry probe carries besides its heading: one bullet, in a list. */
    private static double bulletAndItsList() {
        return capacity().fixedCost(CapacityModel.ITEMIZE_OVERHEAD)
                + capacity().fixedCost(CapacityModel.ITEM_LINE);
    }

    /** One more bullet inside an entry's own list, which is where they are set. */
    private static double perBulletUnderAnEntry() {
        return (delta("beforeEntryThreeItems", "afterEntryThreeItems")
                - delta("beforeOneEntry", "afterOneEntry")) / 2;
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
