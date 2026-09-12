package com.mustafatetik.atomcv.generation.api.dto;

import com.mustafatetik.atomcv.generation.service.WeighedLines;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * What a generation weighed, so a screen can draw a toggle per line (F-031,
 * Bolum 24.4).
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
     * <p>No score. Bolum 23.3's objection to a percentage applies here too —
     * a number beside a bullet invites the reader to treat it as a verdict on
     * the work — and the order already carries everything a ranking says.
     *
     * @param text   what the line said in <em>this</em> CV, which is not
     *               necessarily what the profile says today
     * @param onPage whether it reached the page. The rest competed and lost,
     *               and an edit can put any of them back
     */
    @Schema(name = "SelectionLine", description = "One atom this generation weighed")
    public record SelectionLine(UUID atomId, String text, boolean onPage) {
    }

    public static SelectionViewResponse of(UUID generationId, List<WeighedLines.Line> lines) {
        return new SelectionViewResponse(generationId, lines.stream()
                .map(line -> new SelectionLine(line.atomId(), line.text(), line.onPage()))
                .toList());
    }
}
