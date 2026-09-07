package com.mustafatetik.atomcv.rendering.latex;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import java.util.ArrayList;
import java.util.List;

/**
 * One row of an {@code INLINE_LIST} section: a label, then the list it
 * introduces (Bolum 33.4).
 *
 * <p>A skills matrix is not a paragraph that happens to contain a colon. It is
 * a table with the rule left out — {@code Programming Languages: Java, Python,
 * SQL} — and the label is what a reader scans down. The canonical template
 * sets it in bold and the list after it plain, and until this existed the
 * renderer printed the whole row flat, so a Tech Stack read as six unbroken
 * lines of comma-separated words with nothing to navigate by.
 *
 * <p><strong>The split is a rendering decision, not a content one.</strong>
 * Storing the label as a marked run instead would have been less code and
 * quietly wrong twice over: {@code RichContent.contentHash} is computed over
 * the plain text, so re-marking a row does <em>not</em> invalidate its measured
 * cost — a bolder, wider row would have kept the narrower row's number. And a
 * person who edits a Tech Stack line writes plain text; a shape that only
 * survives while nobody touches it is not a shape the section has.
 *
 * <p>Because it is a rendering decision, the measurement document has to make
 * it too. {@code MeasurableItem} carries the layout for that reason and for no
 * other — Bolum 22.4's rule is that the preamble, the width and the environment
 * all match, and a bold label the measurement never saw breaks the third of
 * them in the one direction that matters: the printed row is wider than the
 * number the page was promised on.
 */
final class InlineRow {

    /**
     * The longest thing that still reads as a label.
     *
     * <p>The real ones run to 34 characters ({@code Data Engineering, BI &
     * Optimization}); this leaves room and still refuses a sentence with a
     * colon in the middle of it, which is what a row that is genuinely prose
     * looks like.
     */
    private static final int MAX_LABEL_CHARS = 60;

    private InlineRow() {
    }

    /**
     * {@code \textbf{Label}{: the rest}}, or the row as it stands when there is
     * no label to find.
     *
     * <p>A row with no colon — a bare list of skills, a sentence — is printed
     * exactly as every other piece of content is. Nothing is invented: the
     * label is text the row already carried, and the only thing added is where
     * the bold starts and stops.
     */
    static String render(RichContent row) {
        String plain = row.plainText();
        int label = labelEnd(plain);
        if (label < 0) {
            return LatexInlineRenderer.render(row);
        }
        return "\\textbf{" + LatexInlineRenderer.render(unstyled(slice(row, 0, label)))
                + "}{" + LatexInlineRenderer.render(unstyled(slice(row, label, plain.length())))
                + "}";
    }

    /**
     * The row, set the way the layout says and no other way.
     *
     * <p>Bolum 22.3 puts "what a mark looks like" in the renderer, and in a row
     * like this one the answer is the same for every mark: nothing. The bold on
     * the label is the whole of the row's styling.
     *
     * <p>Extraction marks what it finds notable, and in a list whose every
     * entry is a technology that comes back as most of the row: a real Tech
     * Stack arrived with seventy per cent of each line marked, which sets
     * seventy per cent of a skills matrix in italic and emphasises none of it.
     * The label goes the same way — a marked {@code Turkish} came out as bold
     * italic where the reference document has bold — because the label is
     * already the emphasis, and emphasising the emphasis says nothing twice.
     *
     * <p>A link keeps its mark, because that one is not decoration: dropping it
     * would turn a destination into text. Nothing is added and no text changes;
     * this decides how what is there is set.
     */
    private static RichContent unstyled(RichContent items) {
        List<Run> plain = new ArrayList<>();
        for (Run run : items.runs()) {
            plain.add(run.href() == null ? Run.of(run.text()) : run);
        }
        return new RichContent(plain);
    }

    /**
     * Where the label stops, or {@code -1} for a row that has none.
     *
     * <p>The colon has to come early, has to have something in front of it that
     * a reader would call a name, and has to be followed by a space — a
     * {@code https://} in the first few characters is not a label, and neither
     * is a row that opens with a colon.
     */
    private static int labelEnd(String plain) {
        int colon = plain.indexOf(':');
        if (colon <= 0 || colon > MAX_LABEL_CHARS) {
            return -1;
        }
        if (colon + 1 < plain.length() && !Character.isWhitespace(plain.charAt(colon + 1))) {
            return -1;
        }
        String label = plain.substring(0, colon);
        return label.chars().anyMatch(Character::isLetter) ? colon : -1;
    }

    /**
     * The runs covering {@code [from, to)}, cut where the boundary falls inside
     * one.
     *
     * <p>A run keeps its marks when it is cut: the halves are the same text
     * said the same way, and a label that lost an emphasis at the colon would
     * be the renderer editing content it was handed. A {@link Run} carrying a
     * link is never split — the href belongs to the whole of it — so a boundary
     * inside one leaves it with the half it started in.
     */
    private static RichContent slice(RichContent content, int from, int to) {
        List<Run> kept = new ArrayList<>();
        int at = 0;
        for (Run run : content.runs()) {
            int start = at;
            int end = at + run.text().length();
            at = end;
            if (end <= from || start >= to) {
                continue;
            }
            if (start >= from && end <= to) {
                kept.add(run);
                continue;
            }
            if (run.href() != null) {
                // Indivisible. It goes with the side it begins on, which keeps
                // the link intact and moves the boundary by at most one run.
                if (start >= from) {
                    kept.add(run);
                }
                continue;
            }
            String text = run.text().substring(
                    Math.max(0, from - start), Math.min(run.text().length(), to - start));
            if (!text.isEmpty()) {
                kept.add(new Run(text, run.marks(), null));
            }
        }
        return new RichContent(kept);
    }
}
