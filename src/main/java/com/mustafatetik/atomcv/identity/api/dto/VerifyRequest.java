package com.mustafatetik.atomcv.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The two halves of Bolum 40.2's link, as the verification page read them out
 * of its own query string.
 *
 * @param selector the public handle, {@code ?s=}
 * @param verifier the secret, {@code ?v=}. It is never stored anywhere — only
 *                 a hash of it is — so this is the only place it exists
 *                 outside the person's inbox
 */
@Schema(description = "Redeem a sign-in link")
public record VerifyRequest(
        @NotBlank @Size(max = 128) String selector,
        @NotBlank @Size(max = 128) String verifier) {
}
