package com.mustafatetik.atomcv.generation.rewrite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What the model answers for the About paragraph (Bolum 21.7).
 *
 * <p>One field, where a rewritten bullet has two. A summary's emphasis is the
 * order of its clauses, not bold type: four bolded technologies in sixty-five
 * words emphasise nothing, and the marks the renderer needs are derived from
 * the skills on this side ({@code RunMarking}).
 *
 * @param text the paragraph
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SynthesisedAbout(String text) {

    public SynthesisedAbout {
        text = text == null ? "" : text;
    }
}
