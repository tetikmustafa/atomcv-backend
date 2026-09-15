package com.mustafatetik.atomcv.ingestion.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Which GitHub account to read (Bolum 31.8).
 *
 * <p>Optional, and absent is the ordinary case: the profile's contact block
 * already carries the account a CV shows an employer, and asking again for
 * something already on the page is a form field nobody should have to fill in
 * twice.
 *
 * @param username a GitHub login, not a URL. Thirty-nine characters is
 *                 GitHub's own ceiling; anything that is not a login is
 *                 refused rather than encoded, because a login that needs
 *                 encoding is not one
 */
@Schema(description = "Which public GitHub account to read")
public record GitHubImportRequest(
        @Schema(description = "A GitHub login; absent reads the one on the profile",
                example = "torvalds")
        @Size(max = 39)
        String username) {
}
