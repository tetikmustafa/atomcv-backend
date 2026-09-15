package com.mustafatetik.atomcv.rendering.template;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One template, as {@code GET /templates} publishes it.
 *
 * <p><strong>The capacity travels, and it is the point.</strong> Bolum 33.5
 * describes the three by how much they hold — about fifty-four lines a page,
 * fifty, sixty-four — and a chooser that showed three names and no density
 * would be asking somebody to pick blind. The figure published is the measured
 * one: the page's text height in points and the height of one line, both from
 * the same constants the selection budget is built from.
 *
 * <p><strong>No display name and no description.</strong> Those are sentences,
 * they are read by a person, and the rule is that the server sends a key and
 * the client writes the sentence. The id is the key.
 *
 * @param version the renderer version. It moves when the geometry
 *                does, which is what invalidates a measured cost -- a client
 *                that caches anything derived from a template should key it on
 *                this
 */
@Schema(description = "A template a CV can be rendered with")
public record TemplateSummary(
        @Schema(description = "The id used everywhere else", example = "classic")
        String id,

        @Schema(description = "Renderer version; moves when the geometry does", example = "6")
        int version,

        @Schema(description = "The text height of one page, in points", example = "648.0")
        double pageTextHeightPt,

        @Schema(description = "One line of body text, in points", example = "13.6")
        double baselineSkipPt,

        @Schema(description = "Roughly how many lines of body text fit a page, "
                + "which is how Bolum 33.5 describes the three", example = "54")
        int approximateLinesPerPage) {

    public static TemplateSummary of(String id) {
        TemplateCustomization defaults = TemplateRegistry.defaultsFor(id);
        CapacityModel capacity = TemplateRegistry.capacityOf(defaults).orElseThrow(
                () -> new IllegalStateException("No capacity for template " + id));

        return new TemplateSummary(
                id,
                TemplateRegistry.versionOf(id),
                capacity.pageTextHeightPt(),
                capacity.baselineSkipPt(),
                (int) Math.floor(capacity.pageTextHeightPt() / capacity.baselineSkipPt()));
    }
}
