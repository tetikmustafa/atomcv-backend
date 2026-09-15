package com.mustafatetik.atomcv.ingestion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The repositories a person picked out of a suggestion list (Bolum 31.8).
 *
 * <p>Names rather than indexes, because the list is a moment old: an index
 * would point at whatever moved into that position, and a name that is no
 * longer there is skipped.
 *
 * @param repositories at most the ten a suggestion list can hold
 */
@Schema(description = "Which suggestions to write")
public record GitHubApplyRequest(
        @Schema(description = "A GitHub login; absent reads the one on the profile",
                example = "torvalds")
        @Size(max = 39)
        String username,

        @Schema(description = "Repository names, as the suggestion list gave them")
        @NotEmpty @Size(max = 10)
        List<@Size(max = 100) String> repositories) {

    public GitHubApplyRequest {
        repositories = repositories == null ? List.of() : List.copyOf(repositories);
    }
}
