package com.mustafatetik.atomcv.compilation;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * A compiler for a clone with no container.
 *
 * <p>{@code make dev} starts the core services and the application; the LaTeX
 * image belongs to {@code make dev-full}. Without this bean Faz E reaches an
 * address nothing is listening on, and "costs nothing, works offline" stops
 * one phase short of a document — which is the same failure the fake LLM
 * provider exists to prevent, one stage further down the pipeline.
 *
 * <p><strong>Nothing here is a measurement, and the difference is the whole
 * design.</strong> A measurement is what a TeX engine said about a document it
 * set; this is arithmetic over the characters in a box. It is enough for Faz C
 * to have numbers to spend, so selection, the budget and the page arithmetic
 * all run offline and their bugs are reachable without Docker — and it is not
 * enough for any claim about a page. A real page count needs {@code make
 * dev-full} or the {@code latexTest} lane.
 *
 * <p><strong>Calibration is deliberately unanswered.</strong> The probes this
 * returns carry no {@code CALIB} lines, so {@link
 * com.mustafatetik.atomcv.rendering.measurement.CalibrationService} finds no
 * probes and refuses to derive a capacity — which is what it already does for
 * a document that did not compile. That refusal is the point: a fabricated
 * calibration would be written to {@code template_capacities} and would
 * outlive the session that invented it, and the built-in constants for classic
 * and compact are right there and real.
 *
 * <p>Costs this <em>does</em> answer are written to the local database like
 * any other, so a dev machine accumulates arithmetic where a server has
 * measurements. That is what {@code make db-reset} is for; nothing under this
 * profile can reach a real one.
 */
@Component
@Profile("local-fake")
@ConditionalOnProperty(prefix = "atomcv.latex", name = "fake", havingValue = "true")
public class FakeLatexCompiler implements LatexCompiler {

    /**
     * The probe the measurement document writes after each box:
     * {@code \typeout{ATOMCOST|key|\the\ht\measurebox|\the\dp\measurebox}}.
     * The key is read from it; the sizes are this class's own answer.
     */
    private static final Pattern PROBE =
            Pattern.compile("\\\\typeout\\{ATOMCOST\\|([^|\\s]+)\\|");

    /** Roughly what a \small line of the canonical template holds. */
    private static final int CHARS_PER_LINE = 90;

    /** The canonical template's {@code \baselineskip} at its own size. */
    private static final double LINE_PT = 12.0;

    /** A box is never zero lines tall, however little is in it. */
    private static final int MIN_LINES = 1;

    @Override
    public CompiledDocument compile(String source) {
        // One page, always, because the honest alternative is a second page
        // model nobody measured. A dev loop wants a document it can open; a
        // page count that moves would be believed, and it would be fiction.
        return new CompiledDocument(onePagePdf(), 1);
    }

    /**
     * The log the parser expects, with a height per probe.
     *
     * <p>The text measured is whatever stands between one probe and the one
     * before it, which is the box the probe reports on. Commands and braces
     * are dropped: {@code \textbf{Go}} prints two characters, not nine, and
     * counting the markup would make a skills row the tallest thing on the
     * page.
     */
    @Override
    public String measure(String source) {
        StringBuilder log = new StringBuilder();
        Matcher probe = PROBE.matcher(source);
        int previous = 0;
        while (probe.find()) {
            String printed = stripMarkup(source.substring(previous, probe.start()));
            previous = probe.end();
            int lines = Math.max(MIN_LINES, (printed.length() + CHARS_PER_LINE - 1)
                    / CHARS_PER_LINE);
            log.append(String.format(Locale.ROOT, "ATOMCOST|%s|%.5fpt|0.00000pt\n",
                    probe.group(1), lines * LINE_PT));
        }
        return log.toString();
    }

    /** What a reader would see: no control sequences, no braces, no runs of space. */
    private static String stripMarkup(String latex) {
        return latex.replaceAll("\\\\[A-Za-z]+\\*?", " ")
                .replaceAll("[{}\\[\\]$&~^_%]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * A valid one-page PDF, built here rather than shipped as a resource.
     *
     * <p>Offsets are computed while the objects are written, because a cross
     * reference table with the wrong numbers is a file some viewers open and
     * others refuse — and a fake that fails in one viewer out of three costs
     * more time than it saves. Latin-1 keeps one byte per character, so the
     * length of the text so far is the offset of what comes next.
     */
    private static byte[] onePagePdf() {
        String content = """
                BT /F1 18 Tf 72 720 Td (AtomCV) Tj ET
                BT /F1 11 Tf 72 690 Td \
                (Fake compiler: local-fake. Nothing here was set by TeX.) Tj ET""";

        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842]"
                        + " /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                "<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i] = pdf.length();
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }

        int startxref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append('\n')
                .append("0000000000 65535 f \n");
        for (int offset : offsets) {
            // Twenty bytes exactly, and "\n" rather than "%n": the platform
            // separator is CRLF on this machine and LF on the runner, and an
            // xref entry of twenty-one bytes is a file half the viewers refuse.
            pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1)
                .append(" /Root 1 0 R >>\nstartxref\n").append(startxref).append("\n%%EOF\n");

        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
}
