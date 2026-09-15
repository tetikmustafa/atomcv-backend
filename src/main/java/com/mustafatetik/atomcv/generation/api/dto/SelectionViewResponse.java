package com.mustafatetik.atomcv.generation.api.dto;

import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.service.WeighedLines;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * What a generation weighed, so a screen can draw a toggle per line (F-031).
 *
 * <p><strong>Every line here is an id the edit endpoint will accept.</strong>
 * That is the whole point of publishing it: the edit refuses an atom this
 * generation never weighed, so a client drawing its controls from today's
 * profile would print buttons that answer 400. Drawn from this list, it cannot.
 *
 * <p>Held-back lines are not capped. The model is shown thirty of them because
 * a prompt is a cost; a person scrolling their own history is not.
 */
@Schema(description = "The atoms a generation weighed, and which of them reached the page")
public record SelectionViewResponse(UUID generationId, List<SelectionLine> lines) {

    /**
     * One line, in the order it is meant to be shown.
     *
     * <p><strong>Still no score, and now the two things a score was standing
     * in for.</strong> The transparency principle asks that the reason for
     * every choice be shown and names three: the score, the matched keywords,
     * and the rejection reason. The number stays off the wire for the same
     * reason a percentage is — it invites the reader to treat it as a verdict
     * on their work, and the order already says everything a ranking says. The
     * other two are not verdicts. They are the evidence, and without them the
     * order was a ranking with no stated grounds, which is the shape that
     * principle exists to rule out.
     *
     * @param text            what the line said in <em>this</em> CV, which is
     *                        not necessarily what the profile says today
     * @param onPage          whether it reached the page. The rest competed and
     *                        lost, and an edit can put any of them back
     * @param matchedKeywords the posting's own terms this line carries. Absent
     *                        rather than empty when there are none: an empty
     *                        array beside a chosen line reads as "matched
     *                        nothing", and in general mode — where there is no
     *                        posting at all — that would be a claim about the
     *                        content rather than about the mode
     * @param heldBackReason  why it is not on the page, absent when it is.
     *                        Four values that send the reader to four different
     *                        places: {@code BUDGET} to the page limit,
     *                        {@code INACTIVE} to the profile editor,
     *                        {@code EXCLUDED_BY_DIRECTIVE} to the edit they
     *                        made on this CV, {@code ENTRY_BELOW_MINIMUM} to
     *                        the entry that went whole
     */
    @Schema(name = "SelectionLine", description = "One atom this generation weighed")
    public record SelectionLine(
            UUID atomId,
            String text,
            boolean onPage,
            @Schema(description = "Posting terms this line carries; absent when there are none")
            List<String> matchedKeywords,
            @Schema(description = "Why it is not on the page; absent when it is")
            SelectionState.RejectionReason heldBackReason) {
    }

    public static SelectionViewResponse of(UUID generationId, List<WeighedLines.Line> lines) {
        return new SelectionViewResponse(generationId, lines.stream()
                .map(line -> new SelectionLine(line.atomId(), line.text(), line.onPage(),
                        line.matchedKeywords().isEmpty() ? null : line.matchedKeywords(),
                        line.heldBackReason()))
                .toList());
    }
}
