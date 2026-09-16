package com.mustafatetik.atomcv.compilation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.rendering.measurement.RenderCost;
import com.mustafatetik.atomcv.rendering.measurement.TexLogParser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * What the offline compiler answers, and what it refuses to.
 *
 * <p>The log is read back with {@link TexLogParser} rather than with a pattern
 * written here: a fake that produces something only its own test can read is a
 * fixture that never intersects the code it stands in for.
 */
class FakeLatexCompilerTest {

    private final FakeLatexCompiler compiler = new FakeLatexCompiler();

    @Test
    void compilesToAPdfOfOnePage() {
        CompiledDocument document = compiler.compile("\\documentclass{article}");

        assertThat(document.pageCount()).isEqualTo(1);
        assertThat(new String(document.pdf(), StandardCharsets.ISO_8859_1))
                .startsWith("%PDF-1.4")
                .endsWith("%%EOF\n");
    }

    /**
     * <strong>The regression this is really for.</strong> {@code %n} writes
     * CRLF on Windows and LF on the runner, and an xref entry is twenty bytes
     * or it is not an xref entry — so the same source would produce a valid
     * file on CI and a broken one on the machine it was written on.
     */
    @Test
    void everyCrossReferenceEntryIsTwentyBytesAndPointsAtItsObject() {
        String pdf = new String(compiler.compile("anything").pdf(), StandardCharsets.ISO_8859_1);

        int table = pdf.indexOf("xref\n0 6\n") + "xref\n0 6\n".length();
        int trailer = pdf.indexOf("trailer\n");
        assertThat(table).as("the table is there").isGreaterThan(0);
        assertThat(trailer - table)
                .as("six entries of exactly twenty bytes")
                .isEqualTo(6 * 20);

        Matcher offset = Pattern.compile("^(\\d{10}) 00000 n $", Pattern.MULTILINE).matcher(pdf);
        int object = 0;
        while (offset.find()) {
            object++;
            assertThat(pdf.substring(Integer.parseInt(offset.group(1))))
                    .as("entry %d points at object %d", object, object)
                    .startsWith(object + " 0 obj");
        }
        assertThat(object).as("one entry per object").isEqualTo(5);
    }

    @Test
    void answersOneCostPerProbeAndAssumesTheBoxItStandsAfter() {
        String source = """
                \\item\\savebox{\\measurebox}{\\small\\parbox{\\linewidth}{\\raggedright Short}}
                \\typeout{ATOMCOST|atom-one|\\the\\ht\\measurebox|\\the\\dp\\measurebox}
                \\item\\savebox{\\measurebox}{\\small\\parbox{\\linewidth}{\\raggedright %s}}
                \\typeout{ATOMCOST|atom-two|\\the\\ht\\measurebox|\\the\\dp\\measurebox}
                """.formatted("word ".repeat(60));

        Map<String, RenderCost> costs = TexLogParser.parseCosts(compiler.measure(source));

        assertThat(costs).containsOnlyKeys("atom-one", "atom-two");
        assertThat(costs.get("atom-one").heightPt()).isEqualTo(12.0);
        assertThat(costs.get("atom-two").heightPt())
                .as("three hundred characters is more than one line of ninety")
                .isGreaterThan(costs.get("atom-one").heightPt());
    }

    /** Markup is not text: {@code \textbf{Go}} prints two characters. */
    @Test
    void doesNotChargeForTheCommandsAroundTheWords() {
        String bare = probe("Go");
        String marked = probe("\\textbf{Go}");

        assertThat(TexLogParser.parseCosts(compiler.measure(marked)))
                .isEqualTo(TexLogParser.parseCosts(compiler.measure(bare)));
    }

    /**
     * A calibration produces no probes here, on purpose: the empty answer is
     * what stops a number nobody measured from being written to
     * {@code template_capacities} and outliving the session that invented it.
     */
    @Test
    void refusesToCalibrate() {
        String calibration = """
                \\typeout{CALIB|textheight|\\the\\textheight}
                \\typeout{CALIB|baselineskip|\\the\\baselineskip}
                """;

        String log = compiler.measure(calibration);

        assertThat(TexLogParser.parseCalibration(log)).isEmpty();
        assertThat(log).isEmpty();
    }

    private static String probe(String content) {
        return "\\savebox{\\measurebox}{" + content + "}\n"
                + "\\typeout{ATOMCOST|k|\\the\\ht\\measurebox|\\the\\dp\\measurebox}\n";
    }
}
