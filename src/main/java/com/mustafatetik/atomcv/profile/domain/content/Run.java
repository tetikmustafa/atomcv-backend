package com.mustafatetik.atomcv.profile.domain.content;

import java.util.List;
import java.util.Objects;

/**
 * A contiguous piece of text carrying zero or more semantic marks (Bolum 12).
 *
 * <p>The run model was chosen over Markdown, character offsets and substring
 * matching: there is no escaping problem, no offset drift when the text is
 * edited, and no ambiguity when the same word occurs twice.
 *
 * @param text  the literal text; never null, may be empty
 * @param marks semantic labels applied to the whole run
 * @param href  target of a {@link Mark#LINK} run; null on every other run
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record Run(String text, List<Mark> marks, String href) {

    public Run {
        Objects.requireNonNull(text, "text");
        marks = marks == null ? List.of() : List.copyOf(marks);

        boolean isLink = marks.contains(Mark.LINK);
        if (isLink && (href == null || href.isBlank())) {
            throw new IllegalArgumentException("A link run must carry an href");
        }
        if (!isLink && href != null) {
            throw new IllegalArgumentException("Only a link run may carry an href");
        }
    }

    public static Run of(String text) {
        return new Run(text, List.of(), null);
    }

    public static Run of(String text, Mark... marks) {
        return new Run(text, List.of(marks), null);
    }

    public static Run link(String text, String href) {
        return new Run(text, List.of(Mark.LINK), href);
    }

    public boolean hasMark(Mark mark) {
        return marks.contains(mark);
    }

    /**
     * Shape only. The text and the href are user content and must never reach a
     * log line (absolute rule 4).
     */
    @Override
    public String toString() {
        return "Run[chars=" + text.length() + ", marks=" + marks + "]";
    }
}
