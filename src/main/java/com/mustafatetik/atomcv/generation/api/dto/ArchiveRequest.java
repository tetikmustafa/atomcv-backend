package com.mustafatetik.atomcv.generation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Which way to set the keep-mark on a generation.
 *
 * <p><strong>One endpoint both ways, as the support grant is</strong>. The
 * resource map names {@code POST /archive} and no undo, and a mark that cannot
 * be taken off is a trap rather than a control — the grant settled the same
 * question the same way, with a boolean in the body of the endpoint that set
 * it.
 *
 * <p>The field is optional and absent means {@code true}: the path already
 * says what a bare POST does, and a request that has to spell out
 * {@code {"archived": true}} to do the thing it is named after is ceremony.
 *
 * @param archived {@code true} or absent to mark it, {@code false} to clear
 */
@Schema(description = "Which way to set the keep-mark")
public record ArchiveRequest(
        @Schema(description = "Absent means archive; false takes the mark off",
                defaultValue = "true")
        Boolean archived) {

    /** Absent is a request to archive. */
    public boolean wanted() {
        return archived == null || archived;
    }
}
