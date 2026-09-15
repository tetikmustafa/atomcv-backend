package com.mustafatetik.atomcv.ingestion.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How many of the chosen repositories were written or merged.
 *
 * <p>A count and not a list of what happened to each. The profile endpoints
 * are what a client reads afterwards and they carry the actual rows; a second
 * description of the same writes would be a second thing to keep true.
 *
 * <p>Fewer than were asked for means some are no longer on the account, which
 * is not a failure: the suggestion list is a moment old.
 */
@Schema(description = "What the import did")
public record GitHubImportResult(
        @Schema(description = "Projects written or merged", example = "3") int applied) {
}
