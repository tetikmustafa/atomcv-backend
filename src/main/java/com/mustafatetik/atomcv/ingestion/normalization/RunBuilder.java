package com.mustafatetik.atomcv.ingestion.normalization;

import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import com.mustafatetik.atomcv.shared.text.SkillNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An emphasis list turned into runs, by first match (Bolum 31.5).
 *
 * <p>The model returns the sentence and, beside it, the substrings worth
 * marking. Bolum 12 stores marked text as runs rather than as offsets or
 * markup, so this is where the two meet: each emphasis is located in the
 * sentence and the sentence is cut around what was found.
 *
 * <p><strong>First match, and overlaps go to whichever starts earlier.</strong>
 * Bolum 31.5 names the rule; the tie-break is the addition, and it is the only
 * one that keeps the output a partition of the input. Two marks over the same
 * characters would need nested runs, which Bolum 12 does not have.
 *
 * <p><strong>An emphasis that is not in the sentence is dropped.</strong> The
 * prompt asks for exact quotations for exactly this reason. A fuzzy match would
 * be the code deciding which words the model meant, and a paraphrase marked as
 * a quotation is worse than no bold at all.
 */
final class RunBuilder {

    private RunBuilder() {
    }

    /**
     * @param text        the sentence, as written
     * @param emphasis    substrings of it, in the model's order
     * @param skills      canonical skill names, for choosing a mark
     * @param metrics     the numbers the sentence claims, likewise
     * @return the sentence as runs; a single unmarked run when nothing matched
     */
    static RichContent build(String text, List<String> emphasis,
            List<String> skills, List<String> metrics) {
        if (text == null || text.isEmpty()) {
            return RichContent.EMPTY;
        }
        List<Span> spans = spansOf(text, emphasis);
        if (spans.isEmpty()) {
            return RichContent.plain(text);
        }
        List<Run> runs = new ArrayList<>();
        int cursor = 0;
        for (Span span : spans) {
            if (span.start() > cursor) {
                runs.add(Run.of(text.substring(cursor, span.start())));
            }
            String marked = text.substring(span.start(), span.end());
            runs.add(Run.of(marked, markFor(marked, skills, metrics)));
            cursor = span.end();
        }
        if (cursor < text.length()) {
            runs.add(Run.of(text.substring(cursor)));
        }
        return new RichContent(runs);
    }

    /**
     * Where each emphasis first appears, with overlaps resolved.
     *
     * <p>Located independently and then sorted, rather than searched forward
     * from a cursor: the model lists what it thought was important, not what
     * comes first, and a forward-only walk would silently drop every emphasis
     * that happened to be listed out of order.
     */
    private static List<Span> spansOf(String text, List<String> emphasis) {
        List<Span> found = new ArrayList<>();
        for (String phrase : emphasis) {
            if (phrase == null || phrase.isBlank()) {
                continue;
            }
            int at = text.indexOf(phrase);
            if (at >= 0) {
                found.add(new Span(at, at + phrase.length()));
            }
        }
        found.sort((left, right) -> Integer.compare(left.start(), right.start()));

        List<Span> kept = new ArrayList<>();
        int end = 0;
        for (Span span : found) {
            if (span.start() >= end) {
                kept.add(span);
                end = span.end();
            }
        }
        return kept;
    }

    /**
     * Which of Bolum 12's marks the span carries.
     *
     * <p>Semantic and never presentational: a renderer decides that a
     * technology is bold, and a different template may decide otherwise.
     *
     * <p><strong>{@code properNouns} does not become {@code ORGANIZATION}.</strong>
     * Bolum 31.4 collects products, employers, institutions and places into one
     * list, so marking any of them as an organisation would be a claim the data
     * does not support — and an unknown mark would render as plain text
     * (Bolum 16.2), losing the emphasis the model asked for. They fall to
     * {@code EMPHASIS}, which is exactly what is known about them.
     */
    private static Mark markFor(String span, List<String> skills, List<String> metrics) {
        String folded = span.strip().toLowerCase(Locale.ROOT);
        if (metrics.stream().anyMatch(metric -> metric.strip().toLowerCase(Locale.ROOT)
                .equals(folded))) {
            return Mark.METRIC;
        }
        String canonical = SkillNames.canonical(span);
        if (skills.stream().anyMatch(skill -> skill.equals(canonical))) {
            return Mark.TECHNOLOGY;
        }
        return Mark.EMPHASIS;
    }

    private record Span(int start, int end) {
    }
}
