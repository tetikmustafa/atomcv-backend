package com.mustafatetik.atomcv.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param email where to send the link. Validated for shape only — whether it
 *              has an account is the one thing the answer must not depend on
 *              (Bolum 40.4)
 */
@Schema(description = "Ask for a sign-in link")
public record MagicLinkRequest(
        @NotBlank @Email @Size(max = 320) String email) {
}
