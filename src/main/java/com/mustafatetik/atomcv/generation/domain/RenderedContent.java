package com.mustafatetik.atomcv.generation.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.List;
import java.util.Locale;

/**
 * {@code generations.content_snapshot}: the words that were actually printed.
 *
 * <p>The selection snapshot next to it holds <em>which</em> atoms were chosen,
 * by id. That is not enough to make the document again, and the gap is not
 * theoretical: the text lives in {@code atom_variants} and the user goes on
 * editing it. Downloading a CV after tightening one bullet would hand back a
 * different document from the one that was sent to an employer — and Stage 3's
 * application tracking points at exactly this row.
 *
 * <p>So the text is copied at generation time. {@link RenderRequest} is already
 * the right shape for it — Bolum 22.2 built it to carry no ids, no scores and
 * no locks, only what prints — which means storing it is storing the render
 * itself, and a download re-runs the same input through the same renderer.
 *
 * <p>The customization and the language are not repeated here; they live in
 * {@link StoredSelection}, and one copy of a fact is easier to keep true than
 * two.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RenderedContent(
        RenderRequest.ProfileHeader header,
        List<RenderRequest.RenderableSection> sections) {

    public RenderedContent {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    public static RenderedContent of(RenderRequest request) {
        return new RenderedContent(request.header(), request.sections());
    }

    public RenderRequest toRenderRequest(TemplateCustomization customization, Locale language) {
        return new RenderRequest(header, sections, customization, language);
    }

    /** Shape only: every field below this is the user's own writing. */
    @Override
    public String toString() {
        return "RenderedContent[sections=" + sections.size() + "]";
    }
}
