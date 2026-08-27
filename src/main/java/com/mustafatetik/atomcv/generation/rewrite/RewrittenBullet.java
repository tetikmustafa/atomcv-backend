package com.mustafatetik.atomcv.generation.rewrite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * What the model answers for one bullet (Bolum 21.4).
 *
 * <p>The same two fields a translation answers with, and for the same reason:
 * the runs are rebuilt on this side from the text and the quotations
 * ({@code RunMarking}), because a model asked to produce markup produces
 * markup that has to be parsed, and a parser is a second place for the CV's
 * text to be altered.
 *
 * @param text     the rewritten line
 * @param emphasis exact quotations from {@code text} worth setting in bold
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RewrittenBullet(String text, List<String> emphasis) {

    public RewrittenBullet {
        text = text == null ? "" : text;
        emphasis = emphasis == null ? List.of() : List.copyOf(emphasis);
    }
}
