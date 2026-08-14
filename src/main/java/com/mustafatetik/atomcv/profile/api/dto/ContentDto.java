package com.mustafatetik.atomcv.profile.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mustafatetik.atomcv.profile.domain.content.ContentMigrator;
import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Atom content on the wire, in the shape of Bolum 14.1.
 *
 * <p>One record for both directions so that the frontend has one type to hold:
 * responses carry {@code v}, requests may leave it out. What a request may not
 * do is claim a version this build does not understand — the reader would have
 * to guess at fields it has never seen.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Content", description = "Semantic runs; no format-specific markup")
public record ContentDto(
        @Schema(description = "Structure version. Server-owned; leave it out when writing.",
                example = "1")
        Integer v,

        @NotNull
        @Size(max = 200, message = "too many runs")
        List<RunDto> runs) {

    /** A stretch of text with the marks that apply to all of it. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "Run")
    public record RunDto(
            @NotNull @Size(max = 4000) @Schema(description = "The text itself", example = "ETL")
            String t,

            @Schema(description = "Semantic marks: technology, metric, emphasis, link, "
                    + "organization. Unknown marks are kept and rendered as plain text.",
                    example = "[\"technology\"]")
            List<String> m,

            @Schema(description = "Only on a run marked link, and required there")
            String href) {
    }

    public static ContentDto of(RichContent content) {
        return new ContentDto(ContentMigrator.CURRENT_VERSION, content.runs().stream()
                .map(run -> new RunDto(
                        run.text(),
                        run.marks().stream().map(Mark::value).toList(),
                        run.href()))
                .toList());
    }

    /**
     * @throws ApiException {@code VALIDATION_FAILED} when a run breaks the
     *                      model's rules — a link without an href, an href on
     *                      something that is not a link, a blank mark, or a
     *                      version this build does not write
     */
    public RichContent toRichContent() {
        if (v != null && v != ContentMigrator.CURRENT_VERSION) {
            throw invalid();
        }
        try {
            return new RichContent(runs.stream()
                    .map(run -> new Run(
                            run.t(),
                            run.m() == null ? List.of() : run.m().stream().map(Mark::new).toList(),
                            run.href()))
                    .toList());
        } catch (IllegalArgumentException | NullPointerException rejected) {
            // The model enforces these invariants in its constructors. Reaching
            // them from a request body makes them the client's mistake, not a
            // server failure — and the catch-all would have called it a 500.
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                .param("fields", List.of("content"))
                .build());
    }
}
